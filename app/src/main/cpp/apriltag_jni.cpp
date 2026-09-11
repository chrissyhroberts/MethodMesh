#include <jni.h>
#include <cmath>
#include <cerrno>
#include <limits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>
#include "apriltag.h"
#include "apriltag_pose.h"
#include "tag16h5.h"
#include "tag25h9.h"
#include "tag36h10.h"
#include "tag36h11.h"
#include "tagCircle21h7.h"
#include "tagCircle49h12.h"
#include "tagCustom48h12.h"
#include "tagStandard41h12.h"
#include "tagStandard52h13.h"

namespace {
struct Detector {
    apriltag_family_t *family = nullptr;
    apriltag_detector_t *detector = nullptr;
    void (*destroyFamily)(apriltag_family_t *) = nullptr;
    ~Detector() {
        if (detector) apriltag_detector_destroy(detector);
        if (family) destroyFamily(family);
    }
};

std::mutex lock;
std::unordered_map<jlong, std::unique_ptr<Detector>> detectors;
jlong nextId = 1;

void error(JNIEnv *env, const char *message) {
    if (!env->ExceptionCheck()) {
        jclass type = env->FindClass("java/lang/IllegalStateException");
        if (type) env->ThrowNew(type, message);
    }
}

void appendNoPose(std::vector<jdouble> &result) {
    const auto nan = std::numeric_limits<jdouble>::quiet_NaN();
    result.push_back(0.0); // pose-present flag
    for (int i = 0; i < 13; ++i) result.push_back(nan); // error, t3, R9
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_methodmesh_platform_fiducial_AprilTagDetector_nativeCreate(
    JNIEnv *env,
    jclass,
    jstring name,
    jdouble decimate,
    jint threads,
    jboolean refineEdges
) {
    try {
        if (!name || !std::isfinite(decimate) || decimate < 1 || decimate > 8 || threads < 1 || threads > 8)
            throw std::invalid_argument("Invalid detector configuration");
        const char *chars = env->GetStringUTFChars(name, nullptr);
        if (!chars) return 0;
        std::string familyName;
        try { familyName = chars; } catch (...) { env->ReleaseStringUTFChars(name, chars); throw; }
        env->ReleaseStringUTFChars(name, chars);

        auto state = std::make_unique<Detector>();
#define FAMILY(f) if (familyName == #f) { state->family = f##_create(); state->destroyFamily = f##_destroy; }
        FAMILY(tag16h5) else FAMILY(tag25h9) else FAMILY(tag36h10) else FAMILY(tag36h11)
        else FAMILY(tagCircle21h7) else FAMILY(tagCircle49h12) else FAMILY(tagCustom48h12)
        else FAMILY(tagStandard41h12) else FAMILY(tagStandard52h13)
        else throw std::invalid_argument("Unsupported AprilTag family");
#undef FAMILY
        if (!state->family) throw std::runtime_error("Cannot create AprilTag family");

        state->detector = apriltag_detector_create();
        if (!state->detector) throw std::runtime_error("Cannot create AprilTag detector");
        state->detector->nthreads = threads;
        state->detector->quad_decimate = decimate;
        state->detector->quad_sigma = 0;
        state->detector->refine_edges = refineEdges == JNI_TRUE;
        errno = 0;
        apriltag_detector_add_family_bits(state->detector, state->family, 1);
        if (errno || !state->family->impl) throw std::runtime_error("Cannot initialize AprilTag decoder");

        std::lock_guard<std::mutex> guard(lock);
        jlong id = nextId++;
        detectors.emplace(id, std::move(state));
        return id;
    } catch (const std::exception &e) {
        error(env, e.what());
        return 0;
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_example_methodmesh_platform_fiducial_AprilTagDetector_nativeDetect(
    JNIEnv *env,
    jobject,
    jlong id,
    jobject buffer,
    jint width,
    jint height,
    jint rowStride,
    jint pixelStride,
    jdouble tagSizeMeters,
    jdouble fx,
    jdouble fy,
    jdouble cx,
    jdouble cy
) {
    try {
        std::lock_guard<std::mutex> guard(lock);
        auto it = detectors.find(id);
        if (it == detectors.end()) throw std::invalid_argument("Detector is closed");

        auto *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
        jlong capacity = env->GetDirectBufferCapacity(buffer);
        if (!data || width < 8 || height < 8 || width > 8192 || height > 8192 ||
            static_cast<int64_t>(width) * height > 16777216 || pixelStride < 1 || pixelStride > 4 ||
            rowStride < (width - 1LL) * pixelStride + 1 ||
            (height - 1LL) * rowStride + (width - 1LL) * pixelStride + 1 > capacity)
            throw std::invalid_argument("Invalid grayscale buffer dimensions or strides");

        const bool wantPose = tagSizeMeters > 0.0;
        if (wantPose && (!std::isfinite(tagSizeMeters) || !std::isfinite(fx) || !std::isfinite(fy) ||
                         !std::isfinite(cx) || !std::isfinite(cy) || fx <= 0.0 || fy <= 0.0))
            throw std::invalid_argument("Invalid tag size or camera intrinsics");

        std::vector<uint8_t> packed;
        if (pixelStride != 1) {
            packed.resize(static_cast<size_t>(width) * height);
            for (int y = 0; y < height; ++y)
                for (int x = 0; x < width; ++x)
                    packed[y * width + x] = data[y * static_cast<int64_t>(rowStride) + x * pixelStride];
            data = packed.data();
            rowStride = width;
        }

        image_u8_t image = {width, height, rowStride, data};
        errno = 0;
        std::unique_ptr<zarray_t, decltype(&apriltag_detections_destroy)> found(
            apriltag_detector_detect(it->second->detector, &image), apriltag_detections_destroy);
        if (!found || errno) throw std::runtime_error("AprilTag detection failed");

        std::vector<jdouble> result;
        result.reserve(static_cast<size_t>(zarray_size(found.get())) * 27);
        for (int i = 0; i < zarray_size(found.get()); ++i) {
            apriltag_detection_t *d = nullptr;
            zarray_get(found.get(), i, &d);
            result.insert(result.end(), {
                static_cast<double>(d->id),
                static_cast<double>(d->hamming),
                d->decision_margin,
                d->c[0],
                d->c[1]
            });
            for (const auto &p : d->p) {
                result.push_back(p[0]);
                result.push_back(p[1]);
            }

            if (!wantPose) {
                appendNoPose(result);
                continue;
            }

            apriltag_detection_info_t info{};
            info.det = d;
            info.tagsize = tagSizeMeters;
            info.fx = fx;
            info.fy = fy;
            info.cx = cx;
            info.cy = cy;
            apriltag_pose_t pose{};
            const double poseError = estimate_tag_pose(&info, &pose);
            const bool validPose = pose.R && pose.t;
            if (!validPose) {
                appendNoPose(result);
                if (pose.R) matd_destroy(pose.R);
                if (pose.t) matd_destroy(pose.t);
                continue;
            }

            result.push_back(1.0);
            result.push_back(poseError);
            result.push_back(MATD_EL(pose.t, 0, 0));
            result.push_back(MATD_EL(pose.t, 1, 0));
            result.push_back(MATD_EL(pose.t, 2, 0));
            for (int r = 0; r < 3; ++r)
                for (int c = 0; c < 3; ++c)
                    result.push_back(MATD_EL(pose.R, r, c));
            matd_destroy(pose.R);
            matd_destroy(pose.t);
        }

        auto output = env->NewDoubleArray(static_cast<jsize>(result.size()));
        if (output) env->SetDoubleArrayRegion(output, 0, static_cast<jsize>(result.size()), result.data());
        return output;
    } catch (const std::exception &e) {
        error(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_methodmesh_platform_fiducial_AprilTagDetector_nativeDestroy(JNIEnv *, jobject, jlong id) {
    std::lock_guard<std::mutex> guard(lock);
    detectors.erase(id);
}
