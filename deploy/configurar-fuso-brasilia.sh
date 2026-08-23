#!/usr/bin/env bash
set -euo pipefail
sudo timedatectl set-timezone America/Sao_Paulo
echo "=== FUSO CONFIGURADO ==="
timedatectl | sed -n '1,8p'
