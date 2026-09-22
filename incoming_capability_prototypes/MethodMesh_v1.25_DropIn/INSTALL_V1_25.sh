#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
SRC="$(cd "$(dirname "$0")" && pwd)"
TARGET="$ROOT/app/src/main"
STAMP="$(date +%Y%m%dT%H%M%S)"
BACKUP="$ROOT/backups_for_rollbacks/methodmesh_v1.25_before_${STAMP}"

if [[ ! -d "$TARGET/java/com/example/methodmesh" ]]; then
  echo "ERROR: $TARGET/java/com/example/methodmesh not found. Pass the MethodMesh repository root." >&2
  exit 2
fi

mkdir -p "$BACKUP"
echo "Backup: $BACKUP"

if [[ -f "$TARGET/java/com/example/methodmesh/MethodMeshApplication.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/MethodMeshApplication.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/MethodMeshApplication.kt" "$BACKUP/java/com/example/methodmesh/MethodMeshApplication.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/MethodMeshApplication.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/MethodMeshApplication.kt" "$TARGET/java/com/example/methodmesh/MethodMeshApplication.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/MethodMeshModule.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/MethodMeshModule.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/MethodMeshModule.kt" "$BACKUP/java/com/example/methodmesh/modules/MethodMeshModule.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/MethodMeshModule.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/MethodMeshModule.kt" "$TARGET/java/com/example/methodmesh/modules/MethodMeshModule.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/ModuleMetadata.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/ModuleMetadata.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/ModuleMetadata.kt" "$BACKUP/java/com/example/methodmesh/modules/ModuleMetadata.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/ModuleMetadata.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/ModuleMetadata.kt" "$TARGET/java/com/example/methodmesh/modules/ModuleMetadata.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/ui/HomeScreen.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/ui/HomeScreen.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/ui/HomeScreen.kt" "$BACKUP/java/com/example/methodmesh/ui/HomeScreen.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/ui/HomeScreen.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/ui/HomeScreen.kt" "$TARGET/java/com/example/methodmesh/ui/HomeScreen.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/transport/OutputFormatter.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/transport/OutputFormatter.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/transport/OutputFormatter.kt" "$BACKUP/java/com/example/methodmesh/transport/OutputFormatter.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/transport/OutputFormatter.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/transport/OutputFormatter.kt" "$TARGET/java/com/example/methodmesh/transport/OutputFormatter.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt" "$BACKUP/java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt" "$TARGET/java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt" "$BACKUP/java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt" "$TARGET/java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt" "$BACKUP/java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt" "$TARGET/java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/ui/components/MetadataBadges.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/ui/components/MetadataBadges.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/ui/components/MetadataBadges.kt" "$BACKUP/java/com/example/methodmesh/ui/components/MetadataBadges.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/ui/components/MetadataBadges.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/ui/components/MetadataBadges.kt" "$TARGET/java/com/example/methodmesh/ui/components/MetadataBadges.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt" "$BACKUP/java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt" "$TARGET/java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt" "$BACKUP/java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt" "$TARGET/java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt" "$BACKUP/java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt" "$TARGET/java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt" "$BACKUP/java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt" "$TARGET/java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt" "$BACKUP/java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt" "$TARGET/java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt" "$BACKUP/java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt" "$TARGET/java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt" "$BACKUP/java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt" "$TARGET/java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt" "$BACKUP/java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt" "$TARGET/java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt" "$BACKUP/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt" "$TARGET/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt" "$BACKUP/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt" "$TARGET/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt" "$TARGET/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt" "$TARGET/java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt" "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt" "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt" "$TARGET/java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt" "$TARGET/java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt" "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt" "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt" "$TARGET/java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt" "$TARGET/java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt" "$TARGET/java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt"

if [[ -f "$TARGET/java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt" ]]; then
  mkdir -p "$BACKUP/$(dirname 'java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt')"
  cp -p "$TARGET/java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt" "$BACKUP/java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt"
fi
mkdir -p "$TARGET/$(dirname 'java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt')"
cp -p "$SRC/DROP_IN/app/src/main/java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt" "$TARGET/java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt"

mkdir -p "$ROOT/docs"
if [[ -f "$ROOT/docs/METHODMESH_MASTER_BOOK.md" ]]; then cp -p "$ROOT/docs/METHODMESH_MASTER_BOOK.md" "$BACKUP/METHODMESH_MASTER_BOOK.md"; fi
cp -p "$SRC/docs/METHODMESH_MASTER_BOOK.md" "$ROOT/docs/METHODMESH_MASTER_BOOK.md"
cp -p "$SRC/docs/TRIAL_PLATFORM_V1_0_SPEC_20260922_r2.md" "$ROOT/docs/TRIAL_PLATFORM_V1_0_SPEC_20260922_r2.md"

echo "Installed MethodMesh v1.25 drop-in files."
echo "Next: ./gradlew :app:compileDebugKotlin"
