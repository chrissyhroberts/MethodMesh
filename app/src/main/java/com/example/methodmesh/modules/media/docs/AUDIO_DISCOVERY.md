# Audio discovery provider note

Default adapter: AudD standard recognition (`https://api.audd.io/`).

Implementation assumptions checked 2026-09-08 against the provider documentation:

- accepts a locally recorded audio file as multipart `file`;
- requires `api_token`;
- returns JSON with a success/error status and, on match, artist/title/album/release date/song link metadata;
- a successful request may legitimately return no match;
- standard endpoint is intended for short recognition clips.

MethodMesh policy for this adapter:

- no end-user media service OAuth/sign-in;
- token is operational configuration only;
- no token in public capability settings or results;
- temporary audio deleted after request;
- no confidence field is invented;
- provider failures are explicit and do not silently fall back to a different account-linked service.

Provider docs: `https://docs.audd.io/`


## Free default

When no private AudD token is stored, v0.4.0 sends the documented public token `test`. AudD documents this as capped at 10 standard-recognition requests/day. The native UI therefore opens ready to identify audio without configuration. A private token can be installed from the Advanced section and remains private to the module.
