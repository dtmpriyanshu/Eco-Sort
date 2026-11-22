"""
EcoSort-Arm Web & Kiosk Server for Raspberry Pi 5 and Arm Linux Devices.
Provides a modern browser interface and REST APIs for smart waste segregation.
"""

import os
import sys
from typing import List, Dict, Optional, Any
from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel

# Ensure root is in path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from core.classifier import WasteClassifier
from core.executorch_engine import ExecuTorchLlamaEngine
from core.telemetry import get_edge_telemetry

app = FastAPI(title="EcoSort-Arm Edge Kiosk", version="5.2.0")
engine = ExecuTorchLlamaEngine()


class AnalyzeRequest(BaseModel):
    query: str
    image_base64: Optional[str] = None


class FollowupRequest(BaseModel):
    question: str
    context_item: str
    history: List[Dict[str, str]] = []


@app.get("/api/telemetry")
async def telemetry_endpoint():
    """Returns real-time Arm SoC hardware metrics."""
    return JSONResponse(get_edge_telemetry())


@app.post("/api/analyze")
async def analyze_endpoint(req: AnalyzeRequest):
    """Analyzes an item name, spoken transcript, or image base64 data."""
    res = engine.analyze_item(req.query, image_base64=req.image_base64)
    return JSONResponse(res.to_dict())


@app.post("/api/followup")
async def followup_endpoint(req: FollowupRequest):
    """Answers follow-up questions using Llama 3.2 reasoning."""
    reply = engine.chat_followup(req.question, req.context_item, req.history)
    return JSONResponse({"reply": reply})


@app.get("/", response_class=HTMLResponse)
async def serve_index(request: Request):
    """Serves the modern, coffee/caramel-styled UI matching the reference design."""
    template_path = os.path.join(os.path.dirname(__file__), "templates", "index.html")
    if os.path.exists(template_path):
        with open(template_path, "r", encoding="utf-8") as f:
            return HTMLResponse(content=f.read())
    return HTMLResponse("<h1>EcoSort-Arm server running.</h1>")
