class CompilerError(Exception):
    """A blocking authoring or compilation error."""


class ValidationError(CompilerError):
    """A source XLSForm violates compiler requirements."""
