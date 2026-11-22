"""
Configuration and Swachh Bharat Mission (SBM 2.0) Waste Stream Definitions.
"""
from dataclasses import dataclass, field
from typing import List, Dict, Any

@dataclass
class WasteStream:
    id: str
    name: str
    bin_name: str
    bin_color: str  # "blue", "green", "red"
    hex_color: str
    description: str
    disposal_rule: str
    badge: str

# Standard Swachh Bharat Mission (SBM 2.0) Streams
STREAM_BLUE = WasteStream(
    id="dry_recyclable",
    name="Dry Recyclable Waste",
    bin_name="BLUE BIN — DRY RECYCLABLE",
    bin_color="blue",
    hex_color="#2563EB",
    description="Clean, uncontaminated dry materials including rigid plastics, multilayer packaging (MLP), paper, cardboard, fabrics, metals, and composite goods.",
    disposal_rule="Keep clean and completely dry. Deposit into the Blue Bin. Flatten containers to minimize transport volume.",
    badge="Dry Stream"
)

STREAM_GREEN = WasteStream(
    id="wet_organic",
    name="Wet Organic & Compostable",
    bin_name="GREEN BIN — WET / COMPOSTABLE",
    bin_color="green",
    hex_color="#16A34A",
    description="Biodegradable kitchen food waste, vegetable/fruit peels, cooked meals, tea leaves, garden clippings, and oil-contaminated paper.",
    disposal_rule="Deposit directly into the Green Bin without plastic bags or liners. Drain excess liquid gravies first.",
    badge="Wet / Compost"
)

STREAM_RED = WasteStream(
    id="domestic_hazardous",
    name="Domestic Hazardous & E-Waste",
    bin_name="RED / BLACK BIN — DOMESTIC HAZARDOUS",
    bin_color="red",
    hex_color="#DC2626",
    description="Electronic peripherals (mouse, chargers, phones), lithium/alkaline batteries, cables, CFL/LED bulbs, chemical packaging, and expired medicines.",
    disposal_rule="CRITICAL: NEVER dispose with regular municipal garbage. Insulate battery terminals with tape and drop at an authorized e-waste collection kiosk.",
    badge="Hazardous / E-Waste"
)
