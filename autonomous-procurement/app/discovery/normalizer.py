from __future__ import annotations

import re
from typing import Optional, Any, Dict

from app.discovery.models import VendorProduct


def normalize_category(category: Optional[str]) -> Optional[str]:
    """Simple normalizer for category strings.

    Keeps it intentionally minimal: lowercasing and stripping.
    Real systems may map synonyms (e.g., 'notebook' -> 'laptop') later.
    """
    if category is None:
        return None
    cat = category.strip().lower()
    if cat == "":
        return None
    return cat


# Patterns
_SCREEN_RE = re.compile(r"(\d{2,3})\s*(?:\"|inches|inch|in)\b", re.IGNORECASE)
_RESOLUTION_MAP = {
    re.compile(r"4\s*k|ultra\s*hd|uhd", re.IGNORECASE): "4K",
    re.compile(r"1080\s*p|full\s*hd|fhd", re.IGNORECASE): "1080p",
    re.compile(r"720\s*p|hd\b", re.IGNORECASE): "720p",
}
_RAM_RE = re.compile(r"(\d+(?:[\.,]\d+)?)\s*(GB|G|TB|T|MB|M)\b", re.IGNORECASE)
_STORAGE_RE = re.compile(r"(\d+(?:[\.,]\d+)?)\s*(GB|G|TB|T|MB|M)\b", re.IGNORECASE)
_WARRANTY_RE = re.compile(r"(\d+)\s*(?:year|years)\b", re.IGNORECASE)
_NUMBER_RE = re.compile(r"(\d+(?:[\.,]\d+)?)")


def _parse_screen(text: str) -> Optional[int]:
    m = _SCREEN_RE.search(text)
    if m:
        try:
            return int(m.group(1))
        except Exception:
            return None
    return None


def _parse_resolution(text: str) -> Optional[str]:
    for pattern, label in _RESOLUTION_MAP.items():
        if pattern.search(text):
            return label
    return None


def _parse_ram_storage(text: str) -> Optional[int]:
    m = _RAM_RE.search(text)
    if not m:
        return None
    num = float(m.group(1).replace(",", ""))
    unit = m.group(2).lower()
    if unit.startswith("t"):
        return int(num * 1024)
    if unit.startswith("m"):
        return max(1, int(num / 1024))
    # GB or G
    return int(num)


def _parse_warranty(text: str) -> Optional[int]:
    m = _WARRANTY_RE.search(text)
    if m:
        try:
            return int(m.group(1))
        except Exception:
            return None
    return None


def _normalize_spec_value(key: str, value: Any) -> Any:
    """Attempt to normalize a single specification value.

    If normalization is not applicable, return the original value.
    """
    if value is None:
        return value
    lk = key.lower().replace("-", "_").strip()
    # operate primarily on strings
    if isinstance(value, str):
        txt = value.strip()
        # screen size
        if lk in ("screen", "screen_size", "screen-size", "display", "display_size", "display-size", "screen_size_inches"):
            screen = _parse_screen(txt)
            if screen:
                return screen
            return txt
        # resolution
        if lk in ("resolution", "display_resolution", "display-resolution"):
            res = _parse_resolution(txt)
            if res:
                return res
            return txt
        # RAM
        if lk in ("ram", "memory", "ram_size", "ram size", "ramsize"):
            parsed = _parse_ram_storage(txt)
            if parsed is not None:
                return parsed
            return txt
        # storage
        if lk in ("storage", "disk", "ssd", "storage size", "storage_size"):
            parsed = _parse_ram_storage(txt)
            if parsed is not None:
                return parsed
            return txt
        # warranty
        if lk in ("warranty", "warranty_years", "warranty_year"):
            parsed = _parse_warranty(txt)
            if parsed is not None:
                return parsed
            return txt
        # fallback: conservative - do not infer from free text
        return value
    # leave other types unchanged
    return value


def normalize_product(product: VendorProduct) -> VendorProduct:
    """Return a new VendorProduct with normalized specifications.

    Strategy:
    - Inspect product_name and specifications dict and extract canonical keys
      such as screen_size (int), resolution (str), ram/storage (int GB),
      warranty_years (int).
    - Preserve unknown specs by copying them unchanged when no rule applies.
    - Keep normalization generic and conservative.
    """
    specs = dict(product.specifications or {})

    # According to the revised strategy, do NOT infer specs from product_name.
    # The normalizer standardizes only the structured specifications dict.

    # Normalize each spec key conservatively
    normalized: Dict[str, Any] = {}
    for k, v in specs.items():
        norm = _normalize_spec_value(k, v)
        key = k.strip()
        lk = key.lower().replace("-", "_")
        # Map variants to conservative canonical keys
        if lk in ("screen", "screen_size", "screen-size", "display", "display_size", "display-size", "screen_size_inches"):
            normalized["screen_size_inches"] = norm
        elif lk in ("resolution", "display_resolution", "display-resolution"):
            # normalize resolution labels (function already handles some cases)
            normalized["resolution"] = norm
        elif lk in ("ram", "memory", "ram size", "ram_size", "ramsize"):
            normalized["ram_gb"] = norm
        elif lk in ("storage", "disk", "ssd", "storage size", "storage_size"):
            normalized["storage_gb"] = norm
        elif lk in ("warranty", "warranty_years", "warranty_year"):
            # avoid duplicating warranty if the product.warranty_years field is already present
            if product.warranty_years is None:
                normalized["warranty_years"] = norm
            else:
                # preserve original key but don't create a duplicate canonical field
                # keep the original (un-normalized) value so we don't duplicate normalized warranty
                normalized[key] = v
        else:
            # preserve unknown specs under their original key
            normalized[key] = norm

    # ensure numeric values are native types (int/float) where we parsed them

    # Build new VendorProduct
    new_product = product.model_copy(update={"specifications": normalized})
    return new_product
