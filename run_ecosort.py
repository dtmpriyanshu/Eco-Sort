"""
Main Launcher for EcoSort-Arm on Raspberry Pi 5, Arm Linux, and Edge Devices.
Starts the Web & Kiosk Interface with full Llama 3.2 ExecuTorch reasoning.
"""

import sys
import os
import uvicorn

# Add project root to sys.path
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from core.telemetry import get_edge_telemetry


def main():
    telem = get_edge_telemetry()
    print("=" * 72)
    print("🌱 EcoSort-Arm: Smart Multimodal Waste Segregation & Circular Economy")
    print("Bharat AI-SoC Challenge 2026–27 | Theme: Environment & Climate (PS 2)")
    print(f"Target Hardware: {telem['soc_model']} ({telem['cpu_arch']})")
    print(f"Runtime Engine:  {telem['runtime']}")
    print("=" * 72)
    print("🚀 Starting EcoSort-Arm Kiosk & API Server on http://0.0.0.0:8000")
    print("🌐 Open http://localhost:8000 or your device IP in your browser.")
    print("=" * 72)

    uvicorn.run("web.server:app", host="0.0.0.0", port=8000, reload=False, log_level="info")


if __name__ == "__main__":
    main()
