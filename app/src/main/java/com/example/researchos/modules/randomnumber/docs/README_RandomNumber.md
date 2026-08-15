# Random number generator

Capability: `random.number.generate`

Generates secure or fixed-seed random numbers with explicit count, range and step controls.

## Intent examples

```text
com.example.researchos.EXECUTE_METHOD(method_id='random.number.generate',input_min='1',input_max='100',input_step='1',return_mode='flat')
```

```text
com.example.researchos.EXECUTE_METHOD(method_id='random.number.generate',input_count='5',input_min='1',input_max='10',input_step='1',input_seed_mode='fixed_seed',input_seed='study001',return_mode='flat')
```

## Inputs

- `input_count`
- `input_min`
- `input_max`
- `input_step`
- `input_seed_mode`: `secure_random` or `fixed_seed`
- `input_seed`: used only with `fixed_seed`

## Outputs

- `random_numbers_json`
- `random_numbers_csv`
- `random_first_number`
- `random_count`
- `random_min`
- `random_max`
- `random_step`
- `random_seed_mode`
- `random_seed`
- `random_algorithm`
- `random_generated_time_iso`
