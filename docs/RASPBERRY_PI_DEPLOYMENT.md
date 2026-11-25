# 🍓 Raspberry Pi 5, Arm Linux & Evaluator Deployment Guide
## EcoSort-Arm: Smart Multimodal Waste Segregation Assistant
**Bharat AI-SoC Challenge 2026–27 | Problem Statement 2**

**Team Name:** ECO FRIENDLY GUY  
**Team Members:** Priyanshu, Vikash, Ashutosh  
**Institution:** North-Eastern Hill University (NEHU), Shillong  
**Faculty Mentor:** [Prof. Subhash C. Arya](https://github.com/aryasubh)  
**Repository:** [https://github.com/dtmpriyanshu/Eco-Sort](https://github.com/dtmpriyanshu/Eco-Sort)  
**Demo Video:** [Watch 2.5-Minute Video Walkthrough (Link to be added)](#)

---

## 1. Quick Testing for Challenge Selectors & Evaluators (On Any Laptop / PC)

Challenge selectors and evaluators do **not** need physical Raspberry Pi or Android hardware to test and evaluate the complete EcoSort system. The codebase is fully cross-platform and can run immediately on **Windows, macOS, or x86/Arm Linux laptops**:

```bash
# 1. Clone the repository
git clone https://github.com/dtmpriyanshu/Eco-Sort.git
cd Eco-Sort

# 2. Set up virtual environment and install dependencies
python -m venv venv

# On Windows (PowerShell):
.\venv\Scripts\Activate.ps1
# On Linux / macOS:
source venv/bin/activate

pip install -r requirements.txt

# 3. Launch the full interactive Web Application:
python run_ecosort.py
```
👉 Open your browser to **`http://localhost:8000`** to experience the exact same coffee/caramel mobile interface with multimodal waste classification, colored bounding boxes, Swachh Bharat advisory, and conversational follow-up Q&A.

Selectors can also run the terminal CLI directly:
```bash
python cli.py --item "plastic bottle"
python cli.py --item "hello"            # Validates non-waste greeting handling
python cli.py --telemetry               # Live CPU, RAM, and inference latency telemetry
```

---

## 2. Raspberry Pi 5 & Arm Linux Deployment

This section covers deploying EcoSort-Arm as a dedicated standalone smart recycling kiosk on Raspberry Pi 5 or Arm-based SBCs.

### Hardware Requirements
- **Device:** Raspberry Pi 5 (4GB or 8GB RAM recommended)
- **OS:** Raspberry Pi OS (64-bit) / Ubuntu 24.04 Server or Desktop for ARM64
- **Processor:** Broadcom BCM2712 Quad-core Arm Cortex-A76 @ 2.4 GHz
- **Optional Vision Accelerator:** Raspberry Pi AI HAT+ (13 TOPS / 26 TOPS with Hailo-8/Hailo-8L NPU)
- **Peripherals:** USB Webcam or Raspberry Pi Camera Module 3, Touchscreen monitor (for kiosk)

---

## 3. Step-by-Step Installation on Raspberry Pi 5

### Step 1: Clone the Repository
Open a terminal on your Raspberry Pi:
```bash
git clone https://github.com/dtmpriyanshu/Eco-Sort.git
cd Eco-Sort
```

### Step 2: Set Up Python Virtual Environment
```bash
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
```

---

## 4. Running EcoSort-Arm on Raspberry Pi 5

### Mode A: Web & Kiosk Interface (Recommended for Public Bins)
Launch the local web server:
```bash
python run_ecosort.py
```
This starts the local FastAPI server on port 8000.
- **On the Pi's own screen:** Open Chromium and go to: `http://localhost:8000`
- **From another computer or phone on the same WiFi/LAN:** Open `http://<raspberry-pi-ip>:8000`

#### Autostarting Kiosk Mode on Boot (for Automated Smart Bins):
To configure the Raspberry Pi to launch EcoSort in full-screen kiosk mode upon power-on:
Create `~/.config/autostart/ecosort.desktop`:
```ini
[Desktop Entry]
Type=Application
Name=EcoSort Kiosk
Exec=chromium-browser --noerrdialogs --disable-infobars --kiosk http://localhost:8000
X-GNOME-Autostart-enabled=true
```

---

### Mode B: Terminal Interactive CLI
For headless SSH sessions or rapid testing:
```bash
# Analyze a specific item:
python cli.py --item "computer mouse"

# Verify non-waste conversational handling:
python cli.py --item "how are you"

# Launch interactive menu with pre-loaded waste samples:
python cli.py

# Check real-time Arm SoC telemetry (Temperature, RAM, Cortex-A cores):
python cli.py --telemetry
```

Sample CLI output:
```
┌────────────────── 📦 Identified: Optical Computer Mouse (E-Waste) ──────────┐
│ RED / BLACK BIN — DOMESTIC HAZARDOUS                                        │
│ ⚠️ CRITICAL: NEVER discard with regular trash. Remove batteries, coil       │
│ wires, and deposit at an authorized municipal e-waste collection kiosk.     │
└─────────────────────────────────────────────────────────────────────────────┘
Advisory & Recycling Science (Llama via ExecuTorch):
• Hydrometallurgical Extraction: Leaches gold, copper, and tin from internal electronics.
• Battery Fire Hazard: Wireless cells ignite under compactor pressure; tape terminals.
Avoided CO₂: 187.0 g  |  Energy Conserved: 90%
⚡ Telemetry: Broadcom BCM2712 (Cortex-A76) | RAM: 2.8 GB free | Latency: 32 ms
```

---

## 5. Raspberry Pi AI HAT+ (Hailo NPU) Dual-Pipeline Architecture

As mandated in the Bharat AI-SoC problem specification:
> *"If a Hailo accelerator is used for vision workloads, vision models run through HailoRT on the Hailo NPU, while Llama runs through ExecuTorch on the Raspberry Pi Arm CPU. These are separate pipelines and should be clearly documented."*

EcoSort-Arm cleanly separates these two workloads:
1. **Hailo NPU Pipeline (`HailoRT`):**
   - Captures frames from the Raspberry Pi Camera Module.
   - Runs object detection / segmentation models (YOLO-Nano INT8) compiled to Hailo `.hef` format.
   - Extracts bounding boxes and physical material properties with zero CPU load.
2. **ExecuTorch Arm CPU Pipeline (`ExecuTorch`):**
   - Receives the structured detection payload.
   - Runs **Llama** quantized model accelerated via **Arm KleidiAI** vector micro-kernels (`kai_matmul`) directly on the Cortex-A76 cores.
   - Generates municipal Swachh Bharat stream assignment, detailed circular recycling science, and environmental impact metrics.

---

## 6. Live Telemetry & Thermal Monitoring

Raspberry Pi 5 features dynamic voltage and frequency scaling (DVFS). EcoSort-Arm monitors SoC health in real-time:
```bash
python cli.py --telemetry
```
It reads:
- Temperature via `vcgencmd measure_temp`
- Arm CPU Architecture (`aarch64 / Cortex-A76`)
- Real-time RAM allocation and free memory
- ExecuTorch inference latency in milliseconds (typically 28–38 ms on Cortex-A76)
