# ODK / XLSForm handoffs

## Encrypt a text item

ODK supplies the field value to `crypto.text.encrypt`. MethodMesh should prompt for the encryption password rather than storing that password in the form. The returned `crypto_value` is a standard compact JWE string suitable for storage in an ordinary text field.

Recommended returned fields:

- `crypto_status`
- `crypto_value`
- `crypto_format`
- `crypto_output_sha256`
- `crypto_provenance_json`
- `crypto_error`

The plaintext SHA-256 is intentionally not returned.

## Encrypt an attachment

ODK supplies the attachment content URI to `crypto.file.encrypt`. MethodMesh returns:

- `crypto_output_uri`
- `crypto_output_filename` (normally `<source>.jwe`)
- `crypto_output_sha256`
- `crypto_provenance_json`

The encrypted file can then be imported/stored by the caller. Decryption after export from the data platform requires only a compatible JOSE/JWE implementation and the password.

## External invocation

The canonical action remains:

`com.example.methodmesh.EXECUTE_METHOD`

with `method_id` selecting the Method. Passwords should not be put into the research record merely to make the external call fully automatic; interactive entry in MethodMesh is preferable for field encryption.
