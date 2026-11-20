# Random number generator

Capability: `random.number.generate`

Generates secure or fixed-seed random numbers with explicit count, range and step controls.

## Native result lifecycle

Ordinary activity recreation retains the generated execution and its original values, execution ID and clock/software evidence. It does not silently generate a replacement. The retained payload is in run-isolated memory, not Android's saved-state Bundle; leaving the screen releases that memory. After process loss, a missing prior result requires an explicit new generation rather than presenting a new draw as the old execution.

## Intent examples

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='random.number.generate',input_min='1',input_max='100',input_step='1',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='random.number.generate',input_count='5',input_min='1',input_max='10',input_step='1',input_seed_mode='fixed_seed',input_seed='study001',return_mode='flat')
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
