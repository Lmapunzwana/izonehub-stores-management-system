#!/bin/bash
set -e

echo "=========================================="
echo "  Deploying Stores Management System"
echo "=========================================="

echo "--> Pulling latest code from main..."
git pull origin main

echo "--> Building backend Docker image..."
docker build -t ghcr.io/lmapunzwana/stores-management-system/backend:latest ./backend

echo "--> Pushing backend Docker image to registry..."
docker push ghcr.io/lmapunzwana/stores-management-system/backend:latest

echo "--> Restarting backend deployment on Kubernetes..."
kubectl rollout restart deployment stores-backend


echo "=========================================="
echo "  Deployment completed successfully!"
echo "=========================================="
