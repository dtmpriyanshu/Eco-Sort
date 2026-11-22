"""
EcoSort Core Package: Edge AI waste classification, Llama advisory, and ExecuTorch runtime on Arm.
"""

from .classifier import WasteClassifier, WasteAnalysisResult
from .executorch_engine import ExecuTorchLlamaEngine
from .telemetry import get_edge_telemetry

__all__ = [
    "WasteClassifier",
    "WasteAnalysisResult",
    "ExecuTorchLlamaEngine",
    "get_edge_telemetry",
]
