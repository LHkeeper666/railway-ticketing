#!/bin/bash

file="铁路售票系统-面试准备.md"
output_file="${file%.md}.pdf"

# pandoc "${file}" -o "${file%.md}.pdf" --pdf-engine=xelatex --filter pandoc-crossref
pandoc "${file}" -o "$output_file" \
    --pdf-engine=xelatex

cp "${file}" /d/code/note/workspace/
