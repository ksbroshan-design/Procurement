from abc import ABC, abstractmethod


class LLMError(Exception):
    """Raised when the language model call or JSON parsing fails."""


class LLMClient(ABC):
    """Provider-agnostic LLM interface used by the intent parser.

    Implementations may call OpenAI, Azure, Ollama, or any other model.
    The parser only depends on this class, never on a vendor SDK.
    """

    @abstractmethod
    def generate_json(
        self,
        system_prompt: str,
        user_prompt: str,
        json_schema: dict,
    ) -> dict:
        """Return a JSON object that follows json_schema.

        Must return a dict, not markdown or plain text.
        """
