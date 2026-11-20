package com.example.methodmesh.transport.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;

/** A real separate-UID file receiver, not a mock ContentResolver in the producer process. */
public class NativeFileShareReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent incoming = getIntent();
        Intent report = new Intent(incoming.getStringExtra("callback_action"))
            .setPackage(incoming.getStringExtra("callback_package"))
            .putExtra("receiver_uid", Process.myUid())
            .putExtra("received_action", incoming.getAction())
            .putExtra("received_type", incoming.getType())
            .putExtra("has_message_text", incoming.hasExtra(Intent.EXTRA_TEXT));
        try {
            ArrayList<Uri> streams;
            if (Intent.ACTION_SEND_MULTIPLE.equals(incoming.getAction())) {
                streams = incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            } else {
                streams = new ArrayList<>();
                streams.add(incoming.getParcelableExtra(Intent.EXTRA_STREAM));
            }
            if (streams == null || streams.isEmpty()) throw new IllegalStateException("No files received");
            ArrayList<String> uris = new ArrayList<>(), hashes = new ArrayList<>(), types = new ArrayList<>(), sizes = new ArrayList<>();
            boolean writable = false;
            for (Uri uri : streams) {
                uris.add(uri.toString()); types.add(getContentResolver().getType(uri));
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); long size = 0;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); size += count; }
                }
                StringBuilder hex = new StringBuilder();
                for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
                hashes.add(hex.toString()); sizes.add(Long.toString(size));
                writable |= checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED;
            }
            ClipData clips = incoming.getClipData();
            ArrayList<String> clipUris = new ArrayList<>(), clipTypes = new ArrayList<>();
            if (clips != null) {
                for (int index = 0; index < clips.getItemCount(); index++) clipUris.add(clips.getItemAt(index).getUri().toString());
                for (int index = 0; index < clips.getDescription().getMimeTypeCount(); index++) clipTypes.add(clips.getDescription().getMimeType(index));
            }
            boolean probeReadable;
            try (InputStream probe = getContentResolver().openInputStream(Uri.parse(incoming.getStringExtra("ungranted_probe")))) {
                probe.read(); probeReadable = true;
            } catch (Exception denied) { probeReadable = false; }
            report.putStringArrayListExtra("streams", uris).putStringArrayListExtra("hashes", hashes)
                .putStringArrayListExtra("mime_types", types).putStringArrayListExtra("sizes", sizes)
                .putStringArrayListExtra("clip_uris", clipUris).putStringArrayListExtra("clip_types", clipTypes)
                .putExtra("writable", writable).putExtra("probe_readable", probeReadable);
        } catch (Exception failure) { report.putExtra("receiver_error", failure.toString()); }
        sendBroadcast(report); finish();
    }
}
