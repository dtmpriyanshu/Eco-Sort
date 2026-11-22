"""
Command-Line Interface (CLI) for EcoSort-Arm.
Designed for headless Raspberry Pi 5, Arm-based Linux boards, and developer terminals.
Supports one-shot queries, sample test suites, and interactive conversational sessions.
"""

import sys
import os
import argparse
from typing import List, Dict

# Ensure project root is in path
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from core.classifier import WasteClassifier
from core.executorch_engine import ExecuTorchLlamaEngine
from core.telemetry import get_edge_telemetry

try:
    from rich.console import Console
    from rich.panel import Panel
    from rich.table import Table
    from rich.text import Text
    USE_RICH = True
    console = Console()
except ImportError:
    USE_RICH = False


def print_banner():
    if USE_RICH:
        banner = Panel.fit(
            "[bold white]🌱 EcoSort-Arm: Smart Multimodal Waste Segregation & Circular Economy Assistant[/bold white]\n"
            "[bold yellow]Bharat AI-SoC Challenge 2026–27 | Theme: Environment & Climate (PS 2)[/bold yellow]\n"
            "[dim]Powered by Llama 3.2 on ExecuTorch with Arm KleidiAI Micro-kernels • Target: Arm Cortex-A[/dim]",
            border_style="bright_blue"
        )
        console.print(banner)
    else:
        print("=" * 70)
        print("EcoSort-Arm: Smart Waste Segregation Assistant (Bharat AI-SoC Challenge PS2)")
        print("Target: Arm Cortex-A (RPi 5 / Android / Arm Linux) | Runtime: ExecuTorch INT4")
        print("=" * 70)


def display_result(res):
    if not res.is_waste_eligible:
        if USE_RICH:
            console.print(f"\n[bold yellow]⚠️ Input Notice:[/bold yellow] {res.disposal_recommendation}\n")
        else:
            print(f"\nInput Notice: {res.disposal_recommendation}\n")
        return

    # Color mapping for Rich console
    color_map = {
        "blue": "bold white on blue",
        "green": "bold white on green",
        "red": "bold white on red"
    }
    style = color_map.get(res.stream.bin_color, "bold white on blue")

    if USE_RICH:
        # 1. Stream Banner
        console.print(Panel(
            f"[bold]{res.stream.bin_name}[/bold]\n"
            f"[italic]{res.disposal_recommendation}[/italic]",
            title=f"📦 Identified: {res.detected_name}",
            style=style
        ))

        # 2. Recycling Details Table
        table = Table(title="🔬 Advisory & Recycling Science (Llama 3.2 via ExecuTorch)", show_header=False, border_style="dim")
        table.add_column("Bullet", style="cyan", width=3)
        table.add_column("Details", style="white")
        for pt in res.recycling_science:
            table.add_row("•", pt)
        console.print(table)

        # 3. Step-by-Step Action Instructions
        act_table = Table(title="📋 Action Instructions for Disposal", show_header=False, border_style="green")
        act_table.add_column("Step", style="bold green", width=4)
        act_table.add_column("Action", style="white")
        for idx, step in enumerate(res.action_instructions, 1):
            act_table.add_row(f"[{idx}]", step)
        console.print(act_table)

        # 4. Environmental Impact Panel
        telem = get_edge_telemetry(res.latency_ms)
        impact_text = (
            f"[bold green]Environmental Benefit:[/bold green] {res.environmental_impact}\n"
            f"[bold cyan]Avoided CO₂:[/bold cyan] {res.avoided_co2_grams} g  |  "
            f"[bold cyan]Energy Conserved:[/bold cyan] {res.energy_saved_percent}%\n"
            f"[dim]⚡ Telemetry: {telem['soc_model']} ({telem['cpu_arch']}) | RAM: {telem['ram_free']} | Latency: {res.latency_ms} ms[/dim]"
        )
        console.print(Panel(impact_text, title="🌍 Circular Economy Impact & Telemetry", border_style="green"))
    else:
        print(f"\n--- IDENTIFIED: {res.detected_name} ---")
        print(f"TARGET STREAM: {res.stream.bin_name}")
        print(f"DISPOSAL ADVICE: {res.disposal_recommendation}\n")
        print("RECYCLING SCIENCE:")
        for pt in res.recycling_science:
            print(f" - {pt}")
        print("\nACTION INSTRUCTIONS:")
        for idx, step in enumerate(res.action_instructions, 1):
            print(f" {idx}. {step}")
        print(f"\nENVIRONMENTAL IMPACT: {res.environmental_impact}")
        print(f"Avoided CO2: {res.avoided_co2_grams}g | Energy Saved: {res.energy_saved_percent}%\n")


def interactive_session():
    print_banner()
    engine = ExecuTorchLlamaEngine()
    current_item = "General Waste"
    history = []

    samples = [
        ("1", "Computer mouse with wire (E-Waste)", "computer mouse"),
        ("2", "Folding umbrella with metal ribs", "umbrella"),
        ("3", "Multi-layered biscuit packet / snack wrapper", "biscuit wrapper"),
        ("4", "Warm woolen blanket / textile", "blanket"),
        ("5", "Greasy cardboard pizza box", "pizza box with oil stains"),
        ("6", "Organic banana peel / fruit scrap", "banana peel"),
        ("7", "Empty soda can / aluminum container", "aluminum can"),
        ("8", "Greeting test: 'Hello'", "hello")
    ]

    print("\nPre-configured Test Samples (Type number or write your own item name):")
    for num, desc, _ in samples:
        print(f"  [{num}] {desc}")
    print("  [q] Quit\n")

    while True:
        try:
            user_input = input("EcoSort > ").strip()
            if not user_input or user_input.lower() in ["q", "quit", "exit"]:
                print("Exiting EcoSort-Arm. Happy sustainable sorting!")
                break

            # Check if user picked a sample number
            selected = None
            for num, _, query in samples:
                if user_input == num:
                    selected = query
                    break
            query_to_run = selected or user_input

            # Run analysis
            res = engine.analyze_item(query_to_run)
            display_result(res)
            if res.is_waste_eligible:
                current_item = res.detected_name

        except (KeyboardInterrupt, EOFError):
            print("\nExiting.")
            break


def main():
    parser = argparse.ArgumentParser(description="EcoSort-Arm CLI for Arm Devices")
    parser.add_argument("--item", type=str, help="Name of waste item to analyze (e.g., 'computer mouse')")
    parser.add_argument("--telemetry", action="store_true", help="Print live hardware SoC telemetry and exit")
    args = parser.parse_args()

    if args.telemetry:
        telem = get_edge_telemetry()
        print("Real-Time Arm SoC Telemetry:")
        for k, v in telem.items():
            print(f"  {k}: {v}")
        return

    if args.item:
        print_banner()
        engine = ExecuTorchLlamaEngine()
        res = engine.analyze_item(args.item)
        display_result(res)
    else:
        interactive_session()


if __name__ == "__main__":
    main()
