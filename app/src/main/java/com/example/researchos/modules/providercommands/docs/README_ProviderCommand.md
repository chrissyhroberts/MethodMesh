# External command library

Capability ID: `provider.command.run`

The external command library lets ResearchOS learn an Android app command once,
save it under a stable name, test it with default values, export/import the
registry, and later call it from an XLSForm, scheduler, or another capability.

The public ResearchOS interface is the saved command ID, not raw Android
activity names or intent internals.

ResearchOS ships with a small bundled command catalogue. Users can run these
immediately, copy them, or save edited versions over the bundled defaults.

## Example intent

```text
com.example.researchos.EXECUTE_METHOD(method_id='provider.command.run',input_provider_command_id='OsmAnd::navigate_to',input_latitude='-1.2864',input_longitude='36.8172',input_label='Clinic entrance',return_mode='flat')
```

## Saved command shape

Each saved command stores:

- command ID, e.g. `OsmAnd::navigate_to`
- provider ID and package name, or package alternatives separated by `|`
- interface type and stability class
- Android action
- optional URI template with `{input}` placeholders
- optional MIME type
- extras template
- default values
- offline support flag
- last test status

## Bundled commands

Current bundled commands include:

| Command ID | Purpose |
|---|---|
| `OsmAnd::show_pin` | Open OsmAnd with a pin at latitude/longitude. |
| `OsmAnd::show_location` | Open OsmAnd centred on supplied latitude/longitude. |
| `OsmAnd::navigate_to` | Open an OsmAnd route between supplied start and finish coordinates. |
| `OsmAnd::geo_search` | Search OsmAnd using a text query. |
| `AndroidMaps::geo_point` | Open any installed maps app at latitude/longitude. |
| `AndroidMaps::geo_search` | Search using any installed maps app. |
| `Browser::open_url` | Open a URL in the default browser or matching app. |
| `Phone::dial_number` | Open the phone dialler with a number. |
| `SMS::compose_message` | Open the SMS app with recipient and body prefilled. |
| `Email::compose_message` | Open an email app with recipient, subject, and body. |
| `AndroidShare::share_text` | Open Android's normal share flow for text. |
| `WhatsApp::send_message` | Open WhatsApp/WhatsApp Business with a prefilled message. |
| `Telegram::open_user` | Open a Telegram username/profile. |
| `WHOeyes::near_vision_test` | Launch the WHOeyes near vision test. |
| `WHOeyes::distance_vision_test` | Launch the WHOeyes distance vision test. |

Bundled commands are normal provider commands. If a user saves a command with
the same command ID, the saved version overrides the bundled version.

Templates support raw placeholders such as `{latitude}` and URL-encoded
placeholders such as `{query_plus}` or `{label_uri}`.

These commands launch the target app. Most third-party apps do not provide a
clean completion callback to return to ResearchOS after the external task is
finished.

Commands are marked by mode:

- `launch_only_intent`: opens the other app but ResearchOS cannot prove the
  external task was completed.
- `activity_result_intent`: opens the other app and expects a returned data
  packet.

Returned activity-result data are captured as JSON in
`provider_returned_values_json` and also flattened as fields named
`provider_return_<returned_key>`.

The bundled WHOeyes commands are based on the example XLSForm integration using
`org.who.whoeyes.share.ACTION_GET_VISION_TEST` with `visiontype=near` or
`visiontype=distant`. WHOeyes is published on Google Play as package
`org.who.whoeyes`.

## Export/import

The whole registry can be copied as JSON and imported on another device.
This is intended to become a portable study integration layer.

## Package variants

Some apps use different Android package names across free, paid, regional, or
forked builds. For example, OsmAnd may appear as `net.osmand` while examples
online may refer to `net.osmand.plus`.

Use alternatives in the package field:

```text
net.osmand|net.osmand.plus
```

When a command runs, ResearchOS tries each package and then falls back to
Android's normal package-free intent resolution.

For OsmAnd routing, a typical saved command is:

```text
command_id=OsmAnd::navigate_to
provider_id=osmand
package_name=net.osmand|net.osmand.plus
action=android.intent.action.VIEW
data_uri_template=https://osmand.net/map/?start={start_latitude},{start_longitude}&finish={finish_latitude},{finish_longitude}&profile={profile}&pin={finish_latitude},{finish_longitude}#{zoom}/{finish_latitude}/{finish_longitude}
```

The bundled `OsmAnd::navigate_to` command uses OsmAnd's documented map URL
style:

```text
https://osmand.net/map/?start={start_latitude},{start_longitude}&finish={finish_latitude},{finish_longitude}&profile={profile}&pin={finish_latitude},{finish_longitude}#{zoom}/{finish_latitude}/{finish_longitude}
```

## Limitations

This first implementation supports intent/activity-result style commands. It
does not yet support callback `PendingIntent`, response broadcasts, deep-link
callbacks, bound services, or AIDL.
