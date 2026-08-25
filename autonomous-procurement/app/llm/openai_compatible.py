import json
import urllib.error
import urllib.request

from app.llm.base import LLMClient, LLMError


class OpenAICompatibleLLM(LLMClient):
    """Calls any OpenAI-compatible Chat Completions HTTP API.

    This uses the standard library only. Swap this class for another
    LLMClient implementation to use a different provider or SDK.
    """

    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str = "https://api.openai.com/v1",
        timeout_seconds: int = 60,
    ) -> None:
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds

    def generate_json(
        self,
        system_prompt: str,
        user_prompt: str,
        json_schema: dict,
    ) -> dict:
        schema_text = json.dumps(json_schema, indent=2)
        payload = {
            "model": self.model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {
                    "role": "system",
                    "content": (
                        f"{system_prompt}\n\n"
                        "Return only valid JSON matching this schema:\n"
                        f"{schema_text}"
                    ),
                },
                {"role": "user", "content": user_prompt},
            ],
        }
        body = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            url=f"{self.base_url}/chat/completions",
            data=body,
            method="POST",
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
        )

        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                raw = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise LLMError(f"LLM HTTP {error.code}: {detail}") from error
        except urllib.error.URLError as error:
            raise LLMError(f"LLM request failed: {error}") from error

        try:
            data = json.loads(raw)
            content = data["choices"][0]["message"]["content"]
        except (json.JSONDecodeError, KeyError, IndexError, TypeError) as error:
            raise LLMError("LLM response was not a valid chat completion") from error

        return parse_json_object(content)


def parse_json_object(text: str) -> dict:
    """Parse a JSON object, ignoring optional markdown code fences."""
    cleaned = text.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        cleaned = "\n".join(lines).strip()

    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError as error:
        raise LLMError("LLM did not return valid JSON") from error

    if not isinstance(parsed, dict):
        raise LLMError("LLM JSON must be an object")
    return parsed
