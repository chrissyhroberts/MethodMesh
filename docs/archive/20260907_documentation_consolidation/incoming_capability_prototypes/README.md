# Incoming capability prototypes

This folder is a quarantine area for capability modules drafted outside Codex Work mode.

Prototype chats should return exactly one canonical module folder, but they may not be able to run the Android build. Keep those folders here until a Work-mode review has:

- checked the module folder shape;
- checked docs and the example ODK/XLSForm;
- fixed compile errors;
- verified the capability in a disposable build;
- reviewed preset/runtime/ODK/result behaviour;
- confirmed the module should be admitted into `app/src/main/java/com/example/methodmesh/modules/`.

Do not leave unverified `*Module.kt` files directly under the real `modules/` folder. MethodMesh auto-discovers modules there, so one broken prototype can break the whole app.
