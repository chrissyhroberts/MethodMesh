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

/** Separate-UID receiver; Java/Android only because the test APK omits app runtime libraries. */
public class AttachmentReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null) {
            Intent child = getIntent().getParcelableExtra("child");
            startActivityForResult(child, 71);
        }
    }

    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 71) return;
        Intent report = new Intent(getIntent().getStringExtra("callback_action"))
            .setPackage(getIntent().getStringExtra("callback_package"))
            .putExtra("receiver_uid", Process.myUid()).putExtra("result_code", result)
            .putExtra("returned_fields", data == null ? (Bundle) null : data.getExtras());
        try {
            ArrayList<String> hashes = new ArrayList<>();
            ArrayList<String> streams = new ArrayList<>();
            boolean writable = false;
            ClipData clips = data == null ? null : data.getClipData();
            if (clips != null) for (int index = 0; index < clips.getItemCount(); index++) {
                Uri uri = clips.getItemAt(index).getUri();
                streams.add(uri.toString());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                }
                StringBuilder hex = new StringBuilder();
                for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
                hashes.add(hex.toString());
                writable |= checkUriPermission(uri, Process.myPid(), Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED;
            }
            boolean probeReadable;
            try (InputStream probe = getContentResolver().openInputStream(
                    Uri.parse(getIntent().getStringExtra("ungranted_probe")))) {
                probe.read();
                probeReadable = true;
            } catch (Exception denied) { probeReadable = false; }
            report.putStringArrayListExtra("hashes", hashes).putStringArrayListExtra("streams", streams)
                .putExtra("writable", writable).putExtra("probe_readable", probeReadable)
                .putExtra("grant_flags", data == null ? 0 : data.getFlags());
        } catch (Exception error) { report.putExtra("receiver_error", error.toString()); }
        sendBroadcast(report);
        finish();
    }
}
