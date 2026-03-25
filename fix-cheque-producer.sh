#!/bin/bash
# Permanent fix for ChequeProducer AmbiguousResolutionException

FILE="ar-adapter-api/src/main/java/com/invoicegenie/ar/adapter/api/rest/ChequeProducer.java"

if [ -f "$FILE" ]; then
    echo "Removing redundant ChequeProducer.java..."
    rm "$FILE"
    echo "✅ ChequeProducer.java removed"
else
    echo "✅ ChequeProducer.java already removed"
fi

echo ""
echo "Building project..."
mvn clean verify -q

if [ $? -eq 0 ]; then
    echo "✅ Build successful"
else
    echo "❌ Build failed"
    exit 1
fi
