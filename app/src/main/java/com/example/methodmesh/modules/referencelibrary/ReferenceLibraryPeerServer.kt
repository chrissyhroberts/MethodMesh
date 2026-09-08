package com.example.methodmesh.modules.referencelibrary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Runtime counters for a single nearby-library session. */
data class ReferenceLibraryPeerStats(
    val uploadedCount: Int = 0,
    val updatedCount: Int = 0,
    val removedCount: Int = 0,
    val shelfChangesCount: Int = 0,
    val bytesReceived: Long = 0L,
    val httpRequestCount: Int = 0
)

/**
 * Tiny module-owned HTTP server used only while the operator has an explicit
 * nearby-library session open. It deliberately has no download endpoint: the
 * peer can upload files and manage library metadata, but MethodMesh does not
 * expose the contents of the existing library over HTTP.
 */
class ReferenceLibraryPeerServer(
    context: Context,
    private val repository: ReferenceLibraryRepository,
    private val sessionToken: String,
    private val defaultShelf: String,
    private val maxFileBytes: Long,
    private val allowEdits: Boolean = true,
    private val onStatsChanged: (ReferenceLibraryPeerStats) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val uploadedCount = AtomicLong(0)
    private val updatedCount = AtomicLong(0)
    private val removedCount = AtomicLong(0)
    private val shelfChangesCount = AtomicLong(0)
    private val bytesReceived = AtomicLong(0)
    private val httpRequestCount = AtomicLong(0)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val port: Int get() = serverSocket?.localPort ?: 0

    fun start(): Int {
        check(!running.get()) { "Nearby library server is already running." }
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
        }
        serverSocket = socket
        running.set(true)
        acceptThread = thread(name = "MethodMesh-reference-library-peer", isDaemon = true) {
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(name = "MethodMesh-reference-library-peer-client", isDaemon = true) {
                    client.use { handleClient(it) }
                }
            }
        }
        return socket.localPort
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
    }

    fun snapshot(): ReferenceLibraryPeerStats = ReferenceLibraryPeerStats(
        uploadedCount = uploadedCount.get().toInt(),
        updatedCount = updatedCount.get().toInt(),
        removedCount = removedCount.get().toInt(),
        shelfChangesCount = shelfChangesCount.get().toInt(),
        bytesReceived = bytesReceived.get(),
        httpRequestCount = httpRequestCount.get().toInt()
    )

    private fun notifyStats() = onStatsChanged(snapshot())

    private fun handleClient(socket: Socket) {
        val remote = socket.inetAddress
        if (!(remote.isLoopbackAddress || remote.isLinkLocalAddress || remote.isSiteLocalAddress)) {
            return
        }
        socket.soTimeout = 30_000
        val input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
        val output = BufferedOutputStream(socket.getOutputStream(), 32 * 1024)
        try {
            val request = readRequest(input) ?: return
            httpRequestCount.incrementAndGet()
            notifyStats()
            val token = request.query["token"] ?: request.headers["x-methodmesh-token"].orEmpty()
            if (!constantTimeEquals(token, sessionToken)) {
                respondText(output, 403, "Forbidden", "This MethodMesh nearby-library link is invalid or has expired.")
                return
            }
            route(request, input, output)
        } catch (error: Throwable) {
            runCatching {
                respondJson(
                    output,
                    500,
                    JSONObject().put("ok", false).put("error", error.message ?: "Local server error")
                )
            }
        } finally {
            runCatching { output.flush() }
        }
    }

    private fun route(request: HttpRequest, input: InputStream, output: OutputStream) {
        when {
            request.method == "GET" && (request.path == "/" || request.path == "/manage") ->
                respondHtml(output, managerPage())

            request.method == "GET" && request.path == "/api/state" ->
                respondJson(output, 200, stateJson())

            request.method == "POST" && request.path == "/api/upload" ->
                upload(request, input, output)

            request.method == "POST" && request.path == "/api/document/update" ->
                updateDocument(request, output)

            request.method == "POST" && request.path == "/api/document/favourite" ->
                favouriteDocument(request, output)

            request.method == "POST" && request.path == "/api/document/remove" ->
                removeDocument(request, output)

            request.method == "POST" && request.path == "/api/shelf/create" ->
                createShelf(request, output)

            request.method == "POST" && request.path == "/api/shelf/delete" ->
                deleteShelf(request, output)

            else -> respondText(output, 404, "Not found", "Unknown nearby-library route.")
        }
    }

    private fun upload(request: HttpRequest, input: InputStream, output: OutputStream) {
        val length = request.contentLength
        if (length <= 0L) {
            respondJson(output, 411, jsonError("Upload size was not supplied."))
            return
        }
        if (length > maxFileBytes) {
            respondJson(output, 413, jsonError("File is larger than the ${maxFileBytes / (1024 * 1024)} MB session limit."))
            return
        }
        val title = request.query["name"].orEmpty().trim().take(180).ifBlank { "Uploaded document" }
        val shelf = validShelf(request.query["shelf"].orEmpty().ifBlank { defaultShelf })
        val mime = request.headers["content-type"].orEmpty().substringBefore(';').trim().ifBlank { "application/octet-stream" }
        val tempDir = File(appContext.cacheDir, "reference_library_peer").apply { mkdirs() }
        val temp = File.createTempFile("nearby-", ".upload", tempDir)
        try {
            temp.outputStream().buffered(64 * 1024).use { fileOutput ->
                copyExactly(input, fileOutput, length)
            }
            val imported = repository.importManagedFile(
                sourceFile = temp,
                title = title,
                shelf = shelf,
                mimeType = mime,
                source = "Nearby upload",
                storageFolder = "nearby"
            )
            uploadedCount.incrementAndGet()
            bytesReceived.addAndGet(length)
            notifyStats()
            respondJson(
                output,
                200,
                JSONObject()
                    .put("ok", true)
                    .put("id", imported.id)
                    .put("title", imported.title)
                    .put("shelf", imported.shelf)
            )
        } finally {
            runCatching { temp.delete() }
        }
    }

    private fun updateDocument(request: HttpRequest, output: OutputStream) {
        if (!allowEdits) return respondJson(output, 403, jsonError("Library editing is disabled for this session."))
        val id = request.query["id"].orEmpty()
        val existing = repository.document(id)
        if (existing == null) return respondJson(output, 404, jsonError("Document not found."))
        val title = request.query["title"].orEmpty().trim().take(180).ifBlank { existing.title }
        val shelf = validShelf(request.query["shelf"].orEmpty().ifBlank { existing.shelf })
        val updated = repository.updateDocument(id, title, shelf)
        if (updated == null) return respondJson(output, 404, jsonError("Document not found."))
        updatedCount.incrementAndGet()
        notifyStats()
        respondJson(output, 200, JSONObject().put("ok", true))
    }

    private fun favouriteDocument(request: HttpRequest, output: OutputStream) {
        if (!allowEdits) return respondJson(output, 403, jsonError("Library editing is disabled for this session."))
        val id = request.query["id"].orEmpty()
        if (repository.document(id) == null) return respondJson(output, 404, jsonError("Document not found."))
        repository.toggleFavourite(id)
        updatedCount.incrementAndGet()
        notifyStats()
        respondJson(output, 200, JSONObject().put("ok", true))
    }

    private fun removeDocument(request: HttpRequest, output: OutputStream) {
        if (!allowEdits) return respondJson(output, 403, jsonError("Library editing is disabled for this session."))
        val id = request.query["id"].orEmpty()
        if (repository.document(id) == null) return respondJson(output, 404, jsonError("Document not found."))
        repository.remove(id)
        removedCount.incrementAndGet()
        notifyStats()
        respondJson(output, 200, JSONObject().put("ok", true))
    }

    private fun createShelf(request: HttpRequest, output: OutputStream) {
        if (!allowEdits) return respondJson(output, 403, jsonError("Library editing is disabled for this session."))
        val label = request.query["label"].orEmpty().trim()
        val created = runCatching { repository.addCustomShelf(label) }
            .getOrElse { return respondJson(output, 400, jsonError(it.message ?: "Could not create shelf.")) }
        shelfChangesCount.incrementAndGet()
        notifyStats()
        respondJson(output, 200, JSONObject().put("ok", true).put("id", created.id).put("label", created.label))
    }

    private fun deleteShelf(request: HttpRequest, output: OutputStream) {
        if (!allowEdits) return respondJson(output, 403, jsonError("Library editing is disabled for this session."))
        val id = request.query["id"].orEmpty()
        val affected = runCatching { repository.deleteCustomShelf(id, "personal") }
            .getOrElse { return respondJson(output, 400, jsonError(it.message ?: "Could not delete shelf.")) }
        shelfChangesCount.incrementAndGet()
        notifyStats()
        respondJson(output, 200, JSONObject().put("ok", true).put("reassigned_documents", affected))
    }

    private fun stateJson(): JSONObject {
        val shelves = REFERENCE_LIBRARY_BUILT_IN_SHELVES + repository.customShelves()
        val shelfJson = JSONArray().apply {
            shelves.forEach { shelf ->
                put(
                    JSONObject()
                        .put("id", shelf.id)
                        .put("label", shelf.label)
                        .put("custom", shelf.id !in REFERENCE_LIBRARY_BUILT_IN_SHELF_IDS)
                )
            }
        }
        val documents = JSONArray().apply {
            repository.documents().sortedBy { it.title.lowercase() }.forEach { document ->
                put(
                    JSONObject()
                        .put("id", document.id)
                        .put("title", document.title)
                        .put("shelf", document.shelf)
                        .put("mime", document.mimeType)
                        .put("source", document.source)
                        .put("favourite", document.favourite)
                )
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("shelves", shelfJson)
            .put("documents", documents)
            .put("stats", JSONObject().apply {
                val stats = snapshot()
                put("uploaded_count", stats.uploadedCount)
                put("updated_count", stats.updatedCount)
                put("removed_count", stats.removedCount)
                put("shelf_changes_count", stats.shelfChangesCount)
                put("bytes_received", stats.bytesReceived)
            })
            .put("max_file_bytes", maxFileBytes)
            .put("allow_edits", allowEdits)
    }

    private fun validShelf(raw: String): String {
        val valid = (REFERENCE_LIBRARY_BUILT_IN_SHELVES + repository.customShelves()).map { it.id }.toSet()
        return raw.takeIf { it in valid } ?: "personal"
    }

    private fun managerPage(): String = MANAGER_HTML.replace("__TOKEN__", urlEncode(sessionToken))

    private data class HttpRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val contentLength: Long
    )

    private fun readRequest(input: InputStream): HttpRequest? {
        val requestLine = readAsciiLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val path = target.substringBefore('?')
        val query = parseQuery(target.substringAfter('?', ""))
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        return HttpRequest(method, path, query, headers, length)
    }

    private fun readAsciiLine(input: InputStream, limit: Int = 16 * 1024): String? {
        val bytes = ArrayList<Byte>(128)
        var previous = -1
        while (bytes.size < limit) {
            val current = input.read()
            if (current < 0) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
            if (previous == '\r'.code && current == '\n'.code) {
                if (bytes.isNotEmpty()) bytes.removeAt(bytes.lastIndex)
                return String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
            }
            bytes.add(current.toByte())
            previous = current
        }
        error("HTTP header line exceeded the local server limit.")
    }

    private fun copyExactly(input: InputStream, output: OutputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) error("Upload ended before the declared file size was received.")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun parseQuery(raw: String): Map<String, String> = raw
        .split('&')
        .mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            urlDecode(key) to urlDecode(value)
        }
        .toMap()

    private fun respondHtml(output: OutputStream, html: String) =
        respond(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))

    private fun respondJson(output: OutputStream, status: Int, json: JSONObject) =
        respond(
            output,
            status,
            statusText(status),
            "application/json; charset=utf-8",
            json.toString().toByteArray(StandardCharsets.UTF_8)
        )

    private fun respondText(output: OutputStream, status: Int, reason: String, body: String) =
        respond(output, status, reason, "text/plain; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun respond(output: OutputStream, status: Int, reason: String, contentType: String, body: ByteArray) {
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)
        output.write(head)
        output.write(body)
        output.flush()
    }

    private fun jsonError(message: String) = JSONObject().put("ok", false).put("error", message)

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        411 -> "Length Required"
        413 -> "Payload Too Large"
        else -> "Error"
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (index in a.indices) diff = diff or (a[index].code xor b[index].code)
        return diff == 0
    }

    companion object {
        private fun urlDecode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private val MANAGER_HTML = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MethodMesh Reference Library</title>
<style>
:root{color-scheme:dark;--bg:#171c1b;--panel:#202826;--panel2:#29322f;--ink:#f4f0e8;--muted:#b9c0bb;--line:#3b4642;--accent:#77b7aa;--accent2:#b9d9d1;--danger:#e2a39f}
*{box-sizing:border-box}body{margin:0;background:linear-gradient(145deg,#121716,#1c2321 45%,#111514);color:var(--ink);font:15px/1.45 system-ui,-apple-system,Segoe UI,sans-serif;min-height:100vh}
main{max-width:1040px;margin:0 auto;padding:42px 24px 70px}.eyebrow{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:var(--accent2);font-weight:700}.hero{display:flex;justify-content:space-between;gap:24px;align-items:end;margin-bottom:28px}.hero h1{font-size:34px;line-height:1.05;margin:7px 0 5px;letter-spacing:-.03em}.hero p{margin:0;color:var(--muted);max-width:640px}.status{background:#18211f;border:1px solid var(--line);border-radius:999px;padding:8px 12px;color:var(--accent2);white-space:nowrap}
.grid{display:grid;grid-template-columns:1.1fr .9fr;gap:18px}@media(max-width:780px){.grid{grid-template-columns:1fr}.hero{align-items:start;flex-direction:column}}
.card{background:rgba(32,40,38,.94);border:1px solid rgba(110,135,127,.24);border-radius:22px;padding:20px;box-shadow:0 18px 50px rgba(0,0,0,.22)}h2{font-size:18px;margin:0 0 5px}small,.muted{color:var(--muted)}
select,input,button{font:inherit}select,input[type=text]{width:100%;background:#151b19;color:var(--ink);border:1px solid var(--line);border-radius:12px;padding:11px 12px;outline:none}select:focus,input:focus{border-color:var(--accent)}button{border:0;border-radius:12px;padding:10px 13px;background:var(--accent);color:#0e1715;font-weight:700;cursor:pointer}button.secondary{background:#303a37;color:var(--ink)}button.ghost{background:transparent;color:var(--accent2);border:1px solid var(--line)}button.danger{background:transparent;color:var(--danger);border:1px solid #674846}
.drop{margin-top:14px;border:1px dashed #668078;border-radius:18px;padding:38px 22px;text-align:center;background:linear-gradient(180deg,rgba(119,183,170,.08),rgba(119,183,170,.02));transition:.15s}.drop.drag{border-color:var(--accent);background:rgba(119,183,170,.14)}.drop strong{display:block;font-size:20px;margin-bottom:4px}.drop input{display:none}.progress{height:7px;background:#111614;border-radius:99px;overflow:hidden;margin-top:10px}.progress>i{display:block;height:100%;width:0;background:var(--accent);transition:width .1s}
.shelf-tools{display:grid;grid-template-columns:1fr auto;gap:8px;margin-top:14px}.shelf-list{display:flex;flex-wrap:wrap;gap:7px;margin-top:12px}.pill{display:flex;align-items:center;gap:8px;border:1px solid var(--line);border-radius:999px;padding:7px 10px;color:var(--muted);background:#171d1b}.pill b{color:var(--ink);font-weight:600}.pill button{padding:1px 6px;border-radius:999px;background:transparent;color:var(--danger)}
.library{margin-top:18px}.library-head{display:flex;justify-content:space-between;align-items:center;margin:0 2px 10px}.count{color:var(--muted)}.doc{display:grid;grid-template-columns:minmax(220px,1fr) 180px 92px;gap:10px;align-items:center;padding:11px 0;border-top:1px solid var(--line)}.doc:first-child{border-top:0}.doc-title{display:flex;align-items:center;gap:9px}.kind{font-size:10px;font-weight:800;letter-spacing:.05em;background:#33413d;color:var(--accent2);border-radius:8px;padding:5px 7px;min-width:40px;text-align:center}.doc input{padding:9px 10px}.actions{display:flex;justify-content:flex-end;gap:5px}.actions button{padding:8px 9px}.empty{padding:28px;text-align:center;color:var(--muted)}@media(max-width:720px){.doc{grid-template-columns:1fr}.actions{justify-content:flex-start}}
.toast{position:fixed;right:18px;bottom:18px;max-width:360px;background:#0f1513;border:1px solid var(--line);border-radius:14px;padding:12px 14px;box-shadow:0 12px 35px rgba(0,0,0,.35);display:none}.privacy{margin-top:18px;color:var(--muted);font-size:13px}
</style>
</head>
<body>
<main>
  <div class="hero"><div><div class="eyebrow">MethodMesh · nearby library</div><h1>Reference library</h1><p>Batch-load documents into the phone, organise shelves, and tidy metadata without cloud storage.</p></div><div class="status" id="status">Local session</div></div>
  <div class="grid">
    <section class="card"><h2>Drop documents into a shelf</h2><div class="muted">Choose the destination once, then drop a whole batch.</div><div style="margin-top:14px"><select id="targetShelf"></select></div>
      <label class="drop" id="drop"><strong>Drop files here</strong><span class="muted">or click to choose multiple files</span><input id="files" type="file" multiple></label>
      <div id="uploadText" class="muted" style="margin-top:10px">Ready.</div><div class="progress"><i id="bar"></i></div>
    </section>
    <section class="card"><h2>Shelves</h2><div class="muted">Create custom shelves here. Deleting a custom shelf moves its documents to Personal.</div><div class="shelf-tools"><input id="newShelf" type="text" maxlength="40" placeholder="New shelf name"><button id="addShelf">Add shelf</button></div><div class="shelf-list" id="shelves"></div></section>
  </div>
  <section class="card library"><div class="library-head"><div><h2>Library</h2><div class="muted">Rename, move or remove entries. Existing document contents are not exposed through this web manager.</div></div><div class="count" id="count"></div></div><div id="documents"></div></section>
  <div class="privacy">This page is served directly by the MethodMesh phone for this temporary session. No cloud service is involved.</div>
</main>
<div class="toast" id="toast"></div>
<script>
const TOKEN='__TOKEN__';
const api=(path,params={})=>{const q=new URLSearchParams(Object.assign({token:TOKEN},params));return path+'?'+q.toString()};
const toast=(m)=>{const t=document.getElementById('toast');t.textContent=m;t.style.display='block';setTimeout(()=>t.style.display='none',2600)};
let state={shelves:[],documents:[]};
function kind(mime,title){const e=(title.split('.').pop()||'').toUpperCase();if(mime&&mime.includes('pdf'))return 'PDF';if(e&&e.length<=6)return e;return 'DOC'}
async function refresh(){const r=await fetch(api('/api/state'),{cache:'no-store'});state=await r.json();render()}
function shelfOptions(selected){const s=document.createElement('select');state.shelves.forEach(x=>{const o=document.createElement('option');o.value=x.id;o.textContent=x.label;o.selected=x.id===selected;s.appendChild(o)});return s}
function render(){
 const editable=state.allow_edits!==false;
 const newShelf=document.getElementById('newShelf');const addShelf=document.getElementById('addShelf');newShelf.disabled=!editable;addShelf.disabled=!editable;
 const target=document.getElementById('targetShelf');const previous=target.value;target.innerHTML='';state.shelves.forEach(x=>{const o=document.createElement('option');o.value=x.id;o.textContent=x.label;target.appendChild(o)});target.value=state.shelves.some(x=>x.id===previous)?previous:(state.shelves.some(x=>x.id==='personal')?'personal':state.shelves[0]?.id||'');
 const shelves=document.getElementById('shelves');shelves.innerHTML='';state.shelves.forEach(x=>{const p=document.createElement('div');p.className='pill';const b=document.createElement('b');b.textContent=x.label;p.appendChild(b);if(x.custom&&editable){const d=document.createElement('button');d.textContent='×';d.title='Delete shelf';d.onclick=async()=>{if(!confirm('Delete shelf “'+x.label+'”? Its documents will move to Personal.'))return;await call('/api/shelf/delete',{id:x.id});};p.appendChild(d)}shelves.appendChild(p)});
 document.getElementById('count').textContent=state.documents.length+' document'+(state.documents.length===1?'':'s');
 const docs=document.getElementById('documents');docs.innerHTML='';if(!state.documents.length){const e=document.createElement('div');e.className='empty';e.textContent='No documents yet.';docs.appendChild(e);return}
 state.documents.forEach(d=>{const row=document.createElement('div');row.className='doc';const left=document.createElement('div');left.className='doc-title';const k=document.createElement('span');k.className='kind';k.textContent=kind(d.mime,d.title);const title=document.createElement('input');title.type='text';title.value=d.title;title.maxLength=180;title.disabled=!editable;title.onchange=()=>updateDoc(d,title.value,shelf.value);left.appendChild(k);left.appendChild(title);const shelf=shelfOptions(d.shelf);shelf.disabled=!editable;shelf.onchange=()=>updateDoc(d,title.value,shelf.value);const actions=document.createElement('div');actions.className='actions';if(editable){const star=document.createElement('button');star.className='secondary';star.textContent=d.favourite?'★':'☆';star.title='Favourite';star.onclick=()=>call('/api/document/favourite',{id:d.id});const rem=document.createElement('button');rem.className='danger';rem.textContent='Remove';rem.onclick=async()=>{if(!confirm('Remove “'+d.title+'” from the library? The stored source file is not deleted.'))return;await call('/api/document/remove',{id:d.id})};actions.appendChild(star);actions.appendChild(rem)}row.appendChild(left);row.appendChild(shelf);row.appendChild(actions);docs.appendChild(row)})
}
async function updateDoc(d,title,shelf){await call('/api/document/update',{id:d.id,title:title,shelf:shelf})}
async function call(path,params){const r=await fetch(api(path,params),{method:'POST'});const j=await r.json();if(!j.ok){toast(j.error||'Request failed');return false}await refresh();return true}
async function uploadFiles(files){if(!files.length)return;const shelf=document.getElementById('targetShelf').value;const text=document.getElementById('uploadText');const bar=document.getElementById('bar');let done=0;for(const file of files){text.textContent='Uploading '+file.name+' · '+(done+1)+' of '+files.length;await new Promise((resolve)=>{const x=new XMLHttpRequest();x.open('POST',api('/api/upload',{name:file.name,shelf:shelf}));x.setRequestHeader('Content-Type',file.type||'application/octet-stream');x.upload.onprogress=(e)=>{if(e.lengthComputable)bar.style.width=((done+e.loaded/e.total)/files.length*100)+'%'};x.onload=()=>{let j={};try{j=JSON.parse(x.responseText)}catch(e){}if(x.status<200||x.status>=300||!j.ok)toast(j.error||('Upload failed: '+file.name));resolve()};x.onerror=()=>{toast('Upload failed: '+file.name);resolve()};x.send(file)});done++}bar.style.width='100%';text.textContent='Finished '+done+' file'+(done===1?'':'s')+'.';await refresh();setTimeout(()=>bar.style.width='0%',900)}
const drop=document.getElementById('drop');const files=document.getElementById('files');drop.onclick=()=>files.click();files.onchange=()=>uploadFiles([...files.files]);['dragenter','dragover'].forEach(n=>drop.addEventListener(n,e=>{e.preventDefault();drop.classList.add('drag')}));['dragleave','drop'].forEach(n=>drop.addEventListener(n,e=>{e.preventDefault();drop.classList.remove('drag')}));drop.addEventListener('drop',e=>uploadFiles([...e.dataTransfer.files]));
document.getElementById('addShelf').onclick=async()=>{const i=document.getElementById('newShelf');if(!i.value.trim())return;if(await call('/api/shelf/create',{label:i.value.trim()}))i.value=''};
refresh().catch(()=>toast('Could not load library state.'));
</script>
</body>
</html>
""".trimIndent()
    }
}

/** Current private IPv4 candidates suitable for typing into a peer browser. */
fun referenceLibraryPrivateIpv4Addresses(): List<String> = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { iface -> Collections.list(iface.inetAddresses).map { iface.name to it } }
        .filter { (_, address) -> address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress }
        .sortedWith(compareBy<Pair<String, InetAddress>> { (name, _) ->
            when {
                name.startsWith("ap", true) || name.contains("softap", true) -> 0
                name.startsWith("wlan", true) || name.startsWith("wifi", true) -> 1
                else -> 2
            }
        }.thenBy { it.first })
        .map { it.second.hostAddress.orEmpty() }
        .filter { it.isNotBlank() }
        .distinct()
}.getOrDefault(emptyList())
