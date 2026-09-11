#include <jni.h>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <string>

extern "C" {
#include "apriltag.h"
#include "apriltag_pose.h"
#include "tag16h5.h"
#include "tag25h9.h"
#include "tag36h11.h"
#include "tagCircle21h7.h"
#include "tagCircle49h12.h"
#include "tagStandard41h12.h"
#include "tagStandard52h13.h"
}

namespace {
struct FamilyHandle {
    apriltag_family_t *family = nullptr;
    void (*destroy)(apriltag_family_t *) = nullptr;
};

FamilyHandle family_for(const std::string &name) {
    if (name == "tag16h5") return {tag16h5_create(), tag16h5_destroy};
    if (name == "tag25h9") return {tag25h9_create(), tag25h9_destroy};
    if (name == "tag36h11") return {tag36h11_create(), tag36h11_destroy};
    if (name == "tagCircle21h7") return {tagCircle21h7_create(), tagCircle21h7_destroy};
    if (name == "tagCircle49h12") return {tagCircle49h12_create(), tagCircle49h12_destroy};
    if (name == "tagStandard52h13") return {tagStandard52h13_create(), tagStandard52h13_destroy};
    return {tagStandard41h12_create(), tagStandard41h12_destroy};
}

std::string jstring_to_utf8(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

void append_number(std::ostringstream &out, double value) {
    if (std::isfinite(value)) out << std::setprecision(12) << value;
    else out << "null";
}
} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_methodmesh_modules_apriltag_AprilTagNativeBridge_nativeDetect(
    JNIEnv *env,
    jclass,
    jbyteArray gray,
    jint width,
    jint height,
    jstring familyName,
    jint threads,
    jdouble quadDecimate,
    jboolean refineEdges,
    jdouble tagSizeMeters,
    jdouble fx,
    jdouble fy,
    jdouble cx,
    jdouble cy
) {
    if (!gray || width <= 0 || height <= 0) {
        return env->NewStringUTF("{\"detections\":[]}");
    }

    const std::string requested = jstring_to_utf8(env, familyName);
    FamilyHandle fh = family_for(requested);
    if (!fh.family) return env->NewStringUTF("{\"detections\":[]}");

    apriltag_detector_t *td = apriltag_detector_create();
    apriltag_detector_add_family(td, fh.family);
    td->nthreads = threads < 1 ? 1 : threads;
    td->quad_decimate = quadDecimate < 1.0 ? 1.0 : quadDecimate;
    td->refine_edges = refineEdges == JNI_TRUE;

    jbyte *pixels = env->GetByteArrayElements(gray, nullptr);
    image_u8_t image{};
    image.width = width;
    image.height = height;
    image.stride = width;
    image.buf = reinterpret_cast<uint8_t *>(pixels);

    zarray_t *detections = apriltag_detector_detect(td, &image);
    std::ostringstream out;
    out << "{\"detections\":[";
    for (int i = 0; i < zarray_size(detections); ++i) {
        apriltag_detection_t *det = nullptr;
        zarray_get(detections, i, &det);
        if (i) out << ',';
        out << "{\"id\":" << det->id
            << ",\"family\":\"" << requested << "\""
            << ",\"hamming\":" << det->hamming
            << ",\"decision_margin\":";
        append_number(out, det->decision_margin);
        out << ",\"center\":[";
        append_number(out, det->c[0]); out << ','; append_number(out, det->c[1]);
        out << "],\"corners\":[";
        for (int c = 0; c < 4; ++c) {
            if (c) out << ',';
            out << '['; append_number(out, det->p[c][0]); out << ','; append_number(out, det->p[c][1]); out << ']';
        }
        out << ']';

        if (tagSizeMeters > 0.0 && fx > 0.0 && fy > 0.0) {
            apriltag_detection_info_t info{};
            info.det = det;
            info.tagsize = tagSizeMeters;
            info.fx = fx;
            info.fy = fy;
            info.cx = cx;
            info.cy = cy;
            apriltag_pose_t pose{};
            const double error = estimate_tag_pose(&info, &pose);
            if (pose.R && pose.t) {
                out << ",\"pose\":{\"t\":[";
                append_number(out, MATD_EL(pose.t, 0, 0)); out << ',';
                append_number(out, MATD_EL(pose.t, 1, 0)); out << ',';
                append_number(out, MATD_EL(pose.t, 2, 0));
                out << "],\"R\":[";
                for (int r = 0; r < 3; ++r) for (int c = 0; c < 3; ++c) {
                    if (r || c) out << ',';
                    append_number(out, MATD_EL(pose.R, r, c));
                }
                out << "],\"error\":"; append_number(out, error); out << '}';
                matd_destroy(pose.R);
                matd_destroy(pose.t);
            }
        }
        out << '}';
    }
    out << "]}";

    apriltag_detections_destroy(detections);
    env->ReleaseByteArrayElements(gray, pixels, JNI_ABORT);
    apriltag_detector_destroy(td);
    fh.destroy(fh.family);
    return env->NewStringUTF(out.str().c_str());
}
