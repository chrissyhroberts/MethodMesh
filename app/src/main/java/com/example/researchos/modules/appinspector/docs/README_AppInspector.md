# Android app inspector

This prototype provides a safe, user-directed way to inspect installed Android applications and test public intent entry points. It is intended for discovering documented or exported integration surfaces, not for bypassing application security.

## Capabilities

- Lists installed applications with a launcher activity.
- Inspects exported activities, services, receivers, and providers.
- Probes common public actions such as `MAIN`, `VIEW`, `EDIT`, `SEND`, and `GET_CONTENT`.
- Tests an explicit action, optional URI, and simple string extras.
- Captures the returned result code, data URI, and returned extras.
- Saves a tested package/component/action/URI/extras combination locally as an integration definition.

Non-exported components, signature-protected components, runtime permissions, authentication, and private implementation details are not bypassed.

## Android intent

The capability is invoked through the standard ResearchOS execution action:

```text
com.example.researchos.EXECUTE_METHOD(method_id='android_app_inspector',return_mode='flat')
```

Optional inputs can be supplied as intent extras or execution context values:

```text
input_package_name
input_test_action
input_test_uri
input_test_extras
```

## Inputs

| Input | Description |
|---|---|
| `input_package_name` | Package to inspect, when starting from an external caller. |
| `input_test_action` | Action to test, for example `android.intent.action.VIEW`. |
| `input_test_uri` | Optional URI for the test intent. |
| `input_test_extras` | Optional newline-separated `key=value` string extras. |

The standalone screen also allows the user to choose an installed application and enter these values interactively.

## Outputs

The compact result includes the selected package, label, version, exported component inventory, discovered common actions, matched public intent-filter probes (action, category, URI scheme, and resolved activities), and—when a test is run—the result code, returned data URI, and returned extras. It also includes a serialized integration definition containing the tested package, component, action, URI, and extras. The result records the inspection time and whether the test launch succeeded or failed.

The integration definition is a local convenience record, not proof that an application has a stable public API. Applications may expose additional undocumented behavior that requires external documentation or source inspection.

## ODK example

The accompanying `example_odk_android_app_inspector.xlsx` demonstrates invoking the capability through `com.example.researchos.EXECUTE_METHOD` and returning the flat result into an ODK group.

This is a diagnostic/integration-builder prototype. A later version can save a tested public intent as a reusable ResearchOS integration definition.
