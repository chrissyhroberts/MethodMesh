MethodMesh Enketo/browser transport patch v5

Fix: browser deep links arrive with Android activity/task routing flags (notably
FLAG_ACTIVITY_NEW_TASK). v4 cloned those flags into ExternalWorkflowActivity
while launching it with StartActivityForResult. Android therefore returned an
immediate result to IntentRouterActivity while the workflow could be launched
in another task, causing Enketo to be restored before the capability ran.

v5 constructs a payload-equivalent workflow intent containing the original
action, URI/data, MIME type, ClipData, extras, and URI permission grants, but
not the caller's activity/task navigation flags. The workflow therefore remains
a true child of IntentRouterActivity until the capability completes.

No XLSForm URL change is required from v4.
