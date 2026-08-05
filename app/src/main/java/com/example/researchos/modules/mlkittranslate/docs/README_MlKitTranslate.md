# ML Kit translation

Capability: `mlkit.translate`

This module manages ML Kit on-device translation models and translates text between supported languages.

## Use cases

- Download language models before fieldwork.
- Remove unused models to save storage.
- Translate short text snippets without sending the text to a server once the required models are installed.

## Intent examples

Translate text:

```text
com.example.researchos.EXECUTE_METHOD(method_id='mlkit.translate',input_source_language='en',input_target_language='fr',input_text='hello world',return_mode='flat')
```

List downloaded language models:

```text
com.example.researchos.EXECUTE_METHOD(method_id='mlkit.translate',input_model_action='list',return_mode='flat')
```

Download a language model:

```text
com.example.researchos.EXECUTE_METHOD(method_id='mlkit.translate',input_model_action='download',input_model_language='fr',return_mode='flat')
```

## Outputs

- `mlkit_translate_source_language`
- `mlkit_translate_target_language`
- `mlkit_translate_input_text`
- `mlkit_translate_text`
- `mlkit_translate_model_action`
- `mlkit_translate_downloaded_models`
- `mlkit_translate_available_languages`
- `mlkit_translate_status`
- `mlkit_translate_error`
- `mlkit_translate_time_iso`

## Notes

Translation is local after models have been downloaded. Downloading and removing language models may require network access and available storage.
