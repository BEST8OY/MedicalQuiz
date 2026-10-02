#!/usr/bin/env python3
import sys
import os
import glob
import json


def analyze(json_path, output_txt="tmp/keepradius/analysis_result.txt"):
    if not os.path.exists(json_path):
        candidates = (
            glob.glob("tmp/keepradius/*.json") +
            glob.glob("tmp/r8analysis/*.json") +
            glob.glob("build/reports/r8/*.json")
        )
        if not candidates:
            print(f"Error: JSON file not found at {json_path}", file=sys.stderr)
            sys.exit(1)
        json_path = sorted(candidates)[-1]
        print(f"Using JSON file: {json_path}")

    try:
        with open(json_path, 'r', encoding='utf-8') as f:
            d = json.load(f)
    except Exception as e:
        print(f"Error loading JSON from {json_path}: {e}", file=sys.stderr)
        sys.exit(1)

    # Build reference map
    c_map = {c.get('id'): set(c.get('constraints', [])) for c in d.get('keep_constraints_table', [])}
    r_map = {r.get('id'): c_map.get(r.get('constraints_id'), set()) for r in d.get('keep_rule_blast_radius_table', [])}

    tot_opt = tot_obf = tot_shr = tot_items = 0

    # Tally constraints across all kept items
    for tbl in ('kept_class_info_table', 'kept_field_info_table', 'kept_method_info_table'):
        for i in d.get(tbl, []):
            tot_items += 1
            kb = i.get('kept_by', [])
            if any('DONT_OPTIMIZE' in r_map.get(r, set()) for r in kb):
                tot_opt += 1
            if any('DONT_OBFUSCATE' in r_map.get(r, set()) for r in kb):
                tot_obf += 1
            if any('DONT_SHRINK' in r_map.get(r, set()) for r in kb):
                tot_shr += 1

    # Find denominator
    bi = d.get('build_info', {})
    live = sum(int(bi.get(k, 0)) for k in ('live_class_count', 'live_field_count', 'live_method_count'))
    denom = live if live > 0 else tot_items

    # Check for globals
    globals_src = [g.get('source', '').lower() for g in d.get('global_keep_rule_blast_radius_table', [])]
    def score(cnt, flag):
        if any(flag in src for src in globals_src):
            return 0.0
        return max(0.0, 100.0 - ((cnt / denom * 100) if denom > 0 else 0))

    opt_score = score(tot_opt, '-dontoptimize')
    obf_score = score(tot_obf, '-dontobfuscate')
    shr_score = score(tot_shr, '-dontshrink')

    # Process keep rules blast radius
    processed = []
    rule_lookup = {}
    for r in d.get('keep_rule_blast_radius_table', []):
        rule_id = r.get('id')
        rule_lookup[rule_id] = r.get('source', '').strip()

    for r in d.get('keep_rule_blast_radius_table', []):
        br = r.get('blast_radius', {})
        c = len(br.get('class_blast_radius', []))
        f = len(br.get('field_blast_radius', []))
        m = len(br.get('method_blast_radius', []))
        impact = c + f + m
        if impact == 0:
            continue
        impact_pct = (impact / denom * 100) if denom > 0 else 0.0
        subsumed_ids = br.get('subsumed_by', [])
        subsumed_sources = [rule_lookup.get(sid, f"Rule #{sid}") for sid in subsumed_ids]

        processed.append({
            'id': r.get('id'),
            'source': r.get('source', '').strip(),
            'impact': impact,
            'impact_pct': impact_pct,
            'classes': c,
            'fields': f,
            'methods': m,
            'subsumed_by': subsumed_sources
        })

    processed.sort(key=lambda x: x['impact'], reverse=True)
    top_rules = [r for r in processed if not r['subsumed_by']][:10]
    subsumed_rules = [r for r in processed if r['subsumed_by']]

    # Format the report per REPORT_FORMAT.md
    lines = []
    lines.append("# R8 Configuration Analyzer Report\n")
    lines.append("## 3. Optimization summary")
    lines.append(f"- **Optimization score**: {opt_score:.2f}% code is available for R8 optimizations (e.g., inlining, merging). {100-opt_score:.2f}% of codebase can't be optimized by R8.")
    lines.append(f"- **Shrinking score**: {shr_score:.2f}% of code will be optimized by R8 by removing unused classes, fields and methods. {100-shr_score:.2f}% of codebase contains redundant classes, fields and methods that can't be removed by R8.")
    lines.append(f"- **Obfuscation score**: {obf_score:.2f}% of the codebase is available for R8 to obfuscate.\n")

    lines.append("## 4. Keep rules evaluation")
    if top_rules:
        for r in top_rules:
            lines.append(f"### `{r['source']}`")
            lines.append(f"- **Keeps**: {r['impact']} items ({r['impact_pct']:.2f}% of codebase). Classes: {r['classes']}, Fields: {r['fields']}, Methods: {r['methods']} are prevented from optimization due to this keep rule.")
            lines.append("- **Action**: Review rule (Refine or Remove if bundled by library)\n")
    else:
        lines.append("No impactful custom keep rules identified.\n")

    if subsumed_rules:
        lines.append("## 5. Subsumed keep rules")
        for r in subsumed_rules:
            lines.append(f"### `{r['source']}`")
            lines.append(f"- **Subsumed By**: `{'`, `'.join(r['subsumed_by'])}`")
            lines.append("- **Action**: **Remove** (subsumed by broader rule).\n")

    report_content = "\n".join(lines)

    # Write to output file
    os.makedirs(os.path.dirname(os.path.abspath(output_txt)), exist_ok=True)
    with open(output_txt, "w", encoding="utf-8") as f:
        f.write(report_content)
    print(f"Report written to: {output_txt}")

    # Also print to stdout
    print("\n" + report_content)

    # If running in GitHub Actions, write to GITHUB_STEP_SUMMARY
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path and os.path.exists(summary_path):
        with open(summary_path, "a", encoding="utf-8") as f:
            f.write("\n" + report_content + "\n")
        print("GitHub Step Summary updated.")


if __name__ == "__main__":
    json_arg = sys.argv[1] if len(sys.argv) > 1 else "tmp/keepradius/keepruleradius.json"
    out_arg = sys.argv[2] if len(sys.argv) > 2 else "tmp/keepradius/analysis_result.txt"
    analyze(json_arg, out_arg)
