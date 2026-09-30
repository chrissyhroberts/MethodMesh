"""Start MethodMeshenger explicitly on power-up."""

try:
    import main
except Exception as error:
    import sys
    sys.print_exception(error)
