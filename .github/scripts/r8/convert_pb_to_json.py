#!/usr/bin/env python3
import sys
import os
import glob
from google.protobuf import json_format

# Ensure local directory is on python path so keep_radius_pb2 can be imported
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import keep_radius_pb2


def convert_pb_to_json(input_pb_path, output_json_path):
    bundle = keep_radius_pb2.BlastRadiusContainer()

    try:
        with open(input_pb_path, "rb") as pb_file:
            binary_data = pb_file.read()
    except Exception as e:
        print(f"Error reading file {input_pb_path}: {e}", file=sys.stderr)
        return False

    try:
        bundle.ParseFromString(binary_data)
    except Exception as e:
        print(f"Error parsing protobuf: {e}", file=sys.stderr)
        return False

    try:
        os.makedirs(os.path.dirname(os.path.abspath(output_json_path)), exist_ok=True)
        json_string = json_format.MessageToJson(
            bundle,
            always_print_fields_with_no_presence=True,
            preserving_proto_field_name=True,
            indent=4
        )
        with open(output_json_path, "w", encoding="utf-8") as json_file:
            json_file.write(json_string)
        print(f"Successfully converted {input_pb_path} -> {output_json_path}")
        return True
    except Exception as e:
        print(f"Error writing JSON: {e}", file=sys.stderr)
        return False


if __name__ == "__main__":
    input_pb = sys.argv[1] if len(sys.argv) > 1 else None
    if not input_pb or not os.path.exists(input_pb):
        candidates = (
            glob.glob("androidApp/build/reports/r8/*.pb") +
            glob.glob("app/build/reports/r8/*.pb") +
            glob.glob("tmp/r8analysis/*.pb") +
            glob.glob("build/reports/r8/*.pb")
        )
        if not candidates:
            print("Error: No .pb file found in search paths", file=sys.stderr)
            sys.exit(1)
        input_pb = sorted(candidates)[-1]
        print(f"Found input protobuf: {input_pb}")

    output_json = sys.argv[2] if len(sys.argv) > 2 else "tmp/keepradius/keepruleradius.json"
    if not convert_pb_to_json(input_pb, output_json):
        sys.exit(1)
