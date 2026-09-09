package com.example.methodmesh.modules.geocaching

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Provider account state. Never contains a provider password or a private trackable tracking code. */
data class ProviderAccount(
    val providerId: String,
    val connected: Boolean,
    val username: String = "",
    val userUuid: String = "",
    val profileUrl: String = "",
    val cachesFound: Int? = null,
    val lastRefreshIso: String = "",
    val message: String = ""
)

data class OAuthRequestToken(val token:String,val secret:String,val authorizationUrl:String)
data class OAuthAccessToken(val token:String,val secret:String)

data class OkapiNearbyResult(val caches:List<CacheRecord>,val providerRecordCount:Int,val more:Boolean)
data class OkapiPublicCheck(val sampleCount:Int,val more:Boolean)

data class OpenCachingInstallation(val id:String,val label:String,val baseUrl:String)

data class GeocachingProviderProfile(
    val id: String,
    val label: String,
    val baseUrl: String,
    val builtIn: Boolean = false
) {
    fun installation() = OpenCachingInstallation(id, label, baseUrl.trimEnd('/'))
    fun toJson() = JSONObject().apply {
        put("id", id); put("label", label); put("base_url", baseUrl.trimEnd('/')); put("built_in", builtIn)
    }
    companion object {
        fun fromJson(o: JSONObject) = GeocachingProviderProfile(
            id = o.optString("id"),
            label = o.optString("label"),
            baseUrl = o.optString("base_url").trimEnd('/'),
            builtIn = o.optBoolean("built_in")
        )
    }
}

/** Persistent registry of OKAPI installations. Credentials remain in GeocachingAccountStore. */
class GeocachingProviderRegistry(private val context: Context) {
    private val prefs = context.getSharedPreferences("geocaching_providers", Context.MODE_PRIVATE)

    fun profiles(): List<GeocachingProviderProfile> {
        val stored = runCatching {
            val raw = prefs.getString("profiles_json", "").orEmpty()
            if (raw.isBlank()) emptyList() else {
                val a = JSONArray(raw)
                buildList { for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(GeocachingProviderProfile.fromJson(it)) } }
            }
        }.getOrDefault(emptyList())
        val byId = stored.filter { it.id.isNotBlank() && it.baseUrl.isNotBlank() }.associateBy { it.id }.toMutableMap()
        starterProfiles().forEach { starter -> byId.putIfAbsent(starter.id, starter) }
        val result = byId.values.sortedWith(compareByDescending<GeocachingProviderProfile> { it.id == defaultProviderIdRaw() }.thenBy { it.label.lowercase() })
        if (stored.isEmpty()) persist(result)
        return result
    }

    fun profile(id: String?): GeocachingProviderProfile? = profiles().firstOrNull { it.id == id }

    fun defaultProviderId(): String {
        val id = defaultProviderIdRaw()
        return profiles().firstOrNull { it.id == id }?.id ?: profiles().first().id
    }

    fun setDefault(id: String) {
        require(profile(id) != null) { "Unknown provider profile: $id" }
        prefs.edit().putString("default_provider_id", id).apply()
    }

    fun save(id: String?, label: String, baseUrl: String): GeocachingProviderProfile {
        val cleanLabel = label.trim().ifBlank { "OpenCaching provider" }
        val cleanUrl = normaliseBaseUrl(baseUrl)
        val existing = id?.let(::profile)
        val targetId = existing?.id ?: uniqueId(cleanLabel)
        val updated = GeocachingProviderProfile(targetId, cleanLabel, cleanUrl, existing?.builtIn ?: false)
        val all = profiles().filterNot { it.id == targetId } + updated
        persist(all)
        if (prefs.getString("default_provider_id", "").isNullOrBlank()) setDefault(targetId)
        return updated
    }

    fun delete(id: String): Boolean {
        val target = profile(id) ?: return false
        if (target.builtIn) return false
        val remaining = profiles().filterNot { it.id == id }
        persist(remaining)
        if (defaultProviderIdRaw() == id) prefs.edit().putString("default_provider_id", remaining.firstOrNull()?.id.orEmpty()).apply()
        return true
    }

    private fun persist(profiles: List<GeocachingProviderProfile>) {
        prefs.edit().putString("profiles_json", JSONArray().apply { profiles.forEach { put(it.toJson()) } }.toString()).apply()
    }

    private fun defaultProviderIdRaw() = prefs.getString("default_provider_id", "opencache_uk").orEmpty().ifBlank { "opencache_uk" }

    private fun uniqueId(label: String): String {
        val stem = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "okapi" }.take(24)
        val used = profiles().map { it.id }.toSet()
        if (stem !in used) return stem
        return "${stem}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun normaliseBaseUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        require(value.startsWith("https://")) { "Provider base URL must use HTTPS." }
        val uri = URI(value)
        require(!uri.host.isNullOrBlank()) { "Enter a valid provider base URL." }
        return value
    }

    companion object {
        val UK = GeocachingProviderProfile("opencache_uk", "OpenCache UK", "https://opencache.uk", builtIn = true)
        val PL = GeocachingProviderProfile("opencaching_pl", "OpenCaching PL", "https://opencaching.pl", builtIn = true)
        fun starterProfiles() = listOf(UK, PL)
    }
}

class GeocachingAccountStore(private val context:Context) {
    private val prefs=context.getSharedPreferences("geocaching_accounts",Context.MODE_PRIVATE)
    private val secure=KeystoreBox("methodmesh.geocaching.oauth")
    fun consumerKey(provider:String)=prefs.getString("$provider.consumer_key","").orEmpty()
    fun consumerSecret(provider:String)=secure.get(prefs,"$provider.consumer_secret")
    fun saveConsumer(provider:String,key:String,secret:String){prefs.edit().putString("$provider.consumer_key",key.trim()).apply();secure.put(prefs,"$provider.consumer_secret",secret.trim())}
    fun accessToken(provider:String)=secure.get(prefs,"$provider.access_token")
    fun accessSecret(provider:String)=secure.get(prefs,"$provider.access_secret")
    fun saveAccess(provider:String,token:String,secret:String){secure.put(prefs,"$provider.access_token",token);secure.put(prefs,"$provider.access_secret",secret)}
    fun username(provider:String)=prefs.getString("$provider.username","").orEmpty()
    fun userUuid(provider:String)=prefs.getString("$provider.user_uuid","").orEmpty()
    fun saveProfile(provider:String,username:String,uuid:String,profileUrl:String,cachesFound:Int?){prefs.edit().putString("$provider.username",username).putString("$provider.user_uuid",uuid).putString("$provider.profile_url",profileUrl).putInt("$provider.caches_found",cachesFound?:-1).putString("$provider.last_refresh",Instant.now().toString()).apply()}
    fun account(provider:String):ProviderAccount=ProviderAccount(provider,accessToken(provider).isNotBlank(),username(provider),userUuid(provider),prefs.getString("$provider.profile_url","").orEmpty(),prefs.getInt("$provider.caches_found",-1).takeIf{it>=0},prefs.getString("$provider.last_refresh","").orEmpty())
    fun clearAccount(provider:String){secure.remove(prefs,"$provider.access_token");secure.remove(prefs,"$provider.access_secret");prefs.edit().remove("$provider.username").remove("$provider.user_uuid").remove("$provider.profile_url").remove("$provider.caches_found").remove("$provider.last_refresh").apply()}
    fun savePending(provider:String,token:String,secret:String){secure.put(prefs,"$provider.pending_token",token);secure.put(prefs,"$provider.pending_secret",secret)}
    fun pending(provider:String):Pair<String,String>?{val token=secure.get(prefs,"$provider.pending_token");val secret=secure.get(prefs,"$provider.pending_secret");return if(token.isBlank()||secret.isBlank())null else token to secret}
    fun clearPending(provider:String){secure.remove(prefs,"$provider.pending_token");secure.remove(prefs,"$provider.pending_secret")}
}

private class KeystoreBox(private val alias:String){
    private val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    private fun key():SecretKey{
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }
    fun put(prefs:android.content.SharedPreferences,name:String,value:String){
        if(value.isBlank()){remove(prefs,name);return}
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val payload=c.iv+c.doFinal(value.toByteArray())
        prefs.edit().putString(name,Base64.encodeToString(payload,Base64.NO_WRAP)).apply()
    }
    fun get(prefs:android.content.SharedPreferences,name:String):String=runCatching{
        val raw=prefs.getString(name,null)?:return@runCatching "";val bytes=Base64.decode(raw,Base64.NO_WRAP);if(bytes.size<=12)return@runCatching ""
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));String(c.doFinal(bytes.copyOfRange(12,bytes.size)))
    }.getOrDefault("")
    fun remove(prefs:android.content.SharedPreferences,name:String){prefs.edit().remove(name).apply()}
}

class OkapiClient(private val installation:OpenCachingInstallation, private val consumerKey:String, private val consumerSecret:String = "") {
    init { require(consumerKey.isNotBlank()) { "OpenCaching consumer key is not configured." } }

    private fun requireConsumerSecret(){ require(consumerSecret.isNotBlank()) { "OpenCaching consumer secret is required for account OAuth, but not for public cache browsing." } }

    fun requestToken():OAuthRequestToken {
        requireConsumerSecret()
        val url="${installation.baseUrl}/okapi/services/oauth/request_token"
        val response=formRequest("POST",url,mapOf("oauth_callback" to "oob"),null,null)
        val p=parseForm(response);val token=p["oauth_token"]?:error("OKAPI did not return a request token.");val secret=p["oauth_token_secret"]?:error("OKAPI did not return a request-token secret.")
        return OAuthRequestToken(token,secret,"${installation.baseUrl}/okapi/services/oauth/authorize?oauth_token=${enc(token)}&interactivity=confirm_user")
    }

    fun accessToken(request:OAuthRequestToken, verifier:String):OAuthAccessToken {
        requireConsumerSecret()
        val url="${installation.baseUrl}/okapi/services/oauth/access_token";val response=formRequest("POST",url,mapOf("oauth_verifier" to verifier.trim()),request.token,request.secret)
        val p=parseForm(response);return OAuthAccessToken(p["oauth_token"]?:error("OKAPI did not return an access token."),p["oauth_token_secret"]?:error("OKAPI did not return an access-token secret."))
    }

    fun account(token:String,secret:String):ProviderAccount {
        val obj=jsonGet("/okapi/services/users/user",mapOf("fields" to "uuid|username|profile_url|caches_found|caches_notfound|caches_hidden"),token,secret)
        return ProviderAccount(installation.id,true,obj.optString("username"),obj.optString("uuid"),obj.optString("profile_url"),obj.optInt("caches_found").takeIf{obj.has("caches_found")},Instant.now().toString())
    }

    fun nearby(latitude:Double,longitude:Double,radiusKm:Double,limit:Int=50,token:String?=null,secret:String?=null):OkapiNearbyResult{
        val searchParams=mapOf("center" to "$latitude|$longitude","radius" to radiusKm.toString(),"limit" to limit.coerceIn(1,200).toString())
        val params=mapOf(
            "search_method" to "services/caches/search/nearest",
            "search_params" to JSONObject(searchParams).toString(),
            "retr_method" to "services/caches/geocaches",
            "retr_params" to JSONObject(mapOf("fields" to "code|name|location|type|status|url|owner|size2|difficulty|terrain|description|hint2|attribution_note|latest_logs","lpc" to 10)).toString(),
            "wrap" to "true"
        )
        val response=jsonGet("/okapi/services/caches/shortcuts/search_and_retrieve",params,token,secret)
        val payload=response.optJSONObject("results") ?: JSONObject()
        val caches=buildList{
            val keys=payload.keys()
            while(keys.hasNext()){
                val code=keys.next()
                val item=payload.optJSONObject(code)?:continue
                if(!item.has("code")) item.put("code",code)
                parseCache(item)?.let(::add)
            }
        }
        return OkapiNearbyResult(caches,payload.length(),response.optBoolean("more",false))
    }

    fun testPublicAccess():OkapiPublicCheck{
        val response=jsonGet("/okapi/services/caches/search/all",mapOf("limit" to "1"),null,null)
        val results=response.optJSONArray("results")?:JSONArray()
        return OkapiPublicCheck(results.length(),response.optBoolean("more",false))
    }

    fun cache(code:String,token:String?=null,secret:String?=null):CacheRecord{
        val o=jsonGet("/okapi/services/caches/geocache",mapOf("cache_code" to code,"fields" to "code|name|location|type|status|url|owner|size2|difficulty|terrain|description|hint2|attribution_note|latest_logs","lpc" to "20"),token,secret)
        return parseCache(o)?:error("Cache $code could not be parsed.")
    }

    fun userLogs(token:String,secret:String,userUuid:String,limit:Int=500):List<JSONObject>{
        requireConsumerSecret()
        require(userUuid.isNotBlank()) { "OpenCaching account profile has no user UUID; refresh the account first." }
        val text=oauthRequest(
            "GET",
            installation.baseUrl+"/okapi/services/logs/userlogs",
            mapOf(
                "user_uuid" to userUuid,
                "fields" to "uuid|cache_code|date|type|comment|date_created|last_modified",
                "limit" to limit.coerceIn(1,1000).toString()
            ),
            token,
            secret
        )
        val arr=JSONArray(text)
        return buildList{for(i in 0 until arr.length()) arr.optJSONObject(i)?.let(::add)}
    }

    fun submitVisit(token:String,secret:String,visit:CacheVisitRecord):Pair<String,String>{
        requireConsumerSecret()
        val type=when(visit.visitType){VisitType.FOUND->"Found it";VisitType.DNF->"Didn't find it";VisitType.NOTE->"Comment"}
        val params=linkedMapOf("cache_code" to visit.cacheCode,"logtype" to type,"comment" to visit.note,"comment_format" to "plaintext","when" to visit.timestamp)
        if(visit.favorite && visit.visitType==VisitType.FOUND) params["recommend"]="true"
        val o=jsonPost("/okapi/services/logs/submit",params,token,secret)
        if(!o.optBoolean("success")) error(o.optString("message","Provider rejected the log."))
        val ids=o.optJSONArray("log_uuids"); val id=ids?.optString(0).orEmpty().ifBlank{o.optString("log_uuid")}
        return id to o.optString("message","Log uploaded.")
    }

    private fun parseCache(o:JSONObject):CacheRecord?{
        val loc=o.optString("location").split('|');if(loc.size<2)return null;val lat=loc[0].toDoubleOrNull()?:return null;val lon=loc[1].toDoubleOrNull()?:return null
        val owner=when(val v=o.opt("owner")){is JSONObject->v.optString("username");else->v?.toString().orEmpty()}
        val logs=buildList{val a=o.optJSONArray("latest_logs")?:JSONArray();for(i in 0 until a.length()){val l=a.optJSONObject(i)?:continue;add("${l.optString("type")} · ${l.optString("date")} · ${stripHtml(l.optString("comment"))}")}}
        return CacheRecord(o.optString("code"),o.optString("name"),when(installation.id){GeocachingProviderRegistry.PL.id->CacheSource.OPENCACHING_PL;GeocachingProviderRegistry.UK.id->CacheSource.OPENCACHE_UK;else->CacheSource.OKAPI},lat,lon,o.optString("type","Traditional"),o.optString("size2","Unknown"),o.optDouble("difficulty").takeUnless{it==0.0||it.isNaN()},o.optDouble("terrain").takeUnless{it==0.0||it.isNaN()},owner,stripHtml(o.optString("description")),o.optString("hint2"),recentLogs=logs,providerUrl=o.optString("url"))
    }

    private fun jsonGet(path:String,params:Map<String,String>,token:String?,secret:String?):JSONObject {
        val url=installation.baseUrl+path
        val body=if(token.isNullOrBlank()) simpleGet(url,params) else oauthRequest("GET",url,params,token,secret)
        return JSONObject(body)
    }
    private fun jsonPost(path:String,params:Map<String,String>,token:String,secret:String):JSONObject = JSONObject(oauthRequest("POST",installation.baseUrl+path,params,token,secret))

    private fun simpleGet(url:String,params:Map<String,String>):String{
        val all=linkedMapOf<String,String>().apply{putAll(params);put("consumer_key",consumerKey)}
        val query=all.entries.joinToString("&"){"${enc(it.key)}=${enc(it.value)}"}
        val connection=(URI(if(query.isNotBlank())"$url?$query" else url).toURL().openConnection() as HttpURLConnection).apply{
            requestMethod="GET";connectTimeout=15000;readTimeout=20000;setRequestProperty("Accept","application/json")
        }
        val code=connection.responseCode
        val stream=if(code in 200..299)connection.inputStream else connection.errorStream
        val body=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
        if(code !in 200..299) error("${installation.label} returned HTTP $code: ${body.take(500)}")
        return body
    }
    private fun formRequest(method:String,url:String,params:Map<String,String>,token:String?,secret:String?):String=oauthRequest(method,url,params,token,secret)

    private fun oauthRequest(method:String,url:String,params:Map<String,String>,token:String?,tokenSecret:String?):String{
        requireConsumerSecret()
        val oauth=linkedMapOf("oauth_consumer_key" to consumerKey,"oauth_nonce" to UUID.randomUUID().toString().replace("-",""),"oauth_signature_method" to "HMAC-SHA1","oauth_timestamp" to (System.currentTimeMillis()/1000).toString(),"oauth_version" to "1.0")
        if(!token.isNullOrBlank())oauth["oauth_token"]=token
        val all=(params+oauth).toSortedMap(compareBy<String>{enc(it)})
        val parameterString=all.entries.sortedWith(compareBy<Map.Entry<String,String>>({enc(it.key)},{enc(it.value)})).joinToString("&"){"${enc(it.key)}=${enc(it.value)}"}
        val base="$method&${enc(url)}&${enc(parameterString)}";val key="${enc(consumerSecret)}&${enc(tokenSecret.orEmpty())}"
        val mac=Mac.getInstance("HmacSHA1");mac.init(SecretKeySpec(key.toByteArray(),"HmacSHA1"));oauth["oauth_signature"]=Base64.encodeToString(mac.doFinal(base.toByteArray()),Base64.NO_WRAP)
        val query=params.entries.joinToString("&"){"${enc(it.key)}=${enc(it.value)}"}
        val connection=(URI(if(method=="GET"&&query.isNotBlank())"$url?$query" else url).toURL().openConnection() as HttpURLConnection).apply{
            requestMethod=method;connectTimeout=15000;readTimeout=20000;setRequestProperty("Authorization","OAuth "+oauth.entries.joinToString(", "){"${enc(it.key)}=\"${enc(it.value)}\""});setRequestProperty("Accept","application/json")
            if(method=="POST"){doOutput=true;setRequestProperty("Content-Type","application/x-www-form-urlencoded");outputStream.use{it.write(query.toByteArray())}}
        }
        val code=connection.responseCode;val stream=if(code in 200..299)connection.inputStream else connection.errorStream;val body=stream?.bufferedReader()?.use{it.readText()}.orEmpty();if(code !in 200..299) error("${installation.label} returned HTTP $code: ${body.take(300)}")
        return body
    }
    private fun parseForm(s:String)=s.split('&').mapNotNull{part->part.split('=',limit=2).takeIf{it.size==2}?.let{java.net.URLDecoder.decode(it[0],"UTF-8") to java.net.URLDecoder.decode(it[1],"UTF-8")}}.toMap()
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8").replace("+","%20").replace("%7E","~")
    private fun stripHtml(s:String)=s.replace(Regex("<[^>]+>")," ").replace(Regex("\\s+")," ").trim()
}
