#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")/.."
resource_root=app/src/main/res
output=$resource_root/raw/translation_contributors.json

if [[ $(git rev-parse --is-shallow-repository) != false ]]; then
  echo "Translation credits require a complete Git history (fetch-depth: 0)" >&2
  exit 1
fi

languages=()
for directory in "$resource_root"/values-*; do
  if [[ -d $directory ]]; then
    languages+=("${directory##*/values-}")
  fi
done

credits=$(
  timeout --verbose 300 git log HEAD --no-merges --format=%x1e%an%x00%s \
    --name-only --regexp-ignore-case --grep='^Translated using Weblate' -- "$resource_root" |
    jq --raw-input --slurp --indent 2 --args '
      def fold:
        ascii_downcase | gsub("ſ"; "s");

      def trim:
        gsub("^([[:space:]]|[\u001c-\u001f])+|([[:space:]]|[\u001c-\u001f])+$"; "");

      def credit:
        select(trim != "")
        | [splits("[\r\n\u000b\u000c\u001c\u001d\u0085\u2028\u2029]")] as $lines
        | ($lines[0] | split("\u0000")) as [$name, $subject]
        | if $subject == null then "Malformed translation commit header\n" | halt_error(1) else . end
        | ($name | trim) as $author
        | select(($author | fold) as $key
            | (["", "weblate:commit", "codeberg translate", "anonymous", "anynymous"] | index($key)) == null)
        | select($subject | fold | startswith("translated using weblate"))
        | $lines[1:][]
        | capture("^app/src/main/res/values-(?<qualifier>[^/]+)/[^/]+\\.xml$")
        | select(.qualifier as $language | $ARGS.positional | index($language))
        | {qualifier, author: $author};

      reduce (split("\u001e")[] | credit) as {$qualifier, $author} ({};
        (.[$qualifier] // []) as $names
        | if ($names | length) < 6 and ($names | index($author)) == null
          then .[$qualifier] = ($names + [$author])
          else .
          end)
      | to_entries
      | sort_by(.key)
      | map({qualifier: .key, contributors: .value})
    ' -- "${languages[@]}"
) || exit 1
if [[ $credits == '[]' ]]; then
  if [[ ${1:-} == --allow-committed-snapshot ]] &&
    git cat-file blob "HEAD:$output" 2>/dev/null | cmp -s - "$output" &&
    jq -e -s '
      def nonblank: type == "string" and test("\\S");
      length == 1 and (.[0] | type == "array" and length > 0 and all(.[];
        type == "object"
        and (.qualifier | nonblank)
        and (.contributors | type == "array" and length > 0 and all(.[]; nonblank))
      ))
    ' "$output" >/dev/null; then
    echo "Using the committed translation credits snapshot"
    exit 0
  fi
  echo "No translation credits found in Git history" >&2
  exit 1
fi

printf '%s\n' "$credits" > "$output"
printf 'Generated translation credits for %s languages\n' "$(jq 'length' <<< "$credits")"
