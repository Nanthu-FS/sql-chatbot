import os
import tempfile
import time
from collections import Counter

import cv2
import numpy as np
import pandas as pd
import plotly.graph_objects as go
import streamlit as st
import supervision as sv
from ultralytics import YOLO

from activity_classifier import ActivityClassifier, ACTIVITIES, ACTIVITY_COLORS

# ── Page config ──────────────────────────────────────────────────────────────
st.set_page_config(
    page_title="Workplace Activity Analyzer",
    page_icon="🏢",
    layout="wide",
)

st.markdown("""
<style>
  [data-testid="stSidebar"] { background: #1a1a2e; }
  [data-testid="stSidebar"] * { color: #e0e0e0 !important; }
  .metric-card {
    background: #16213e; border-radius: 10px; padding: 16px 20px;
    text-align: center; color: #e0e0e0;
  }
  .metric-card .val { font-size: 2rem; font-weight: 700; color: #00d4aa; }
  .metric-card .lbl { font-size: 0.85rem; color: #aaa; margin-top: 4px; }
  .activity-badge {
    display: inline-block; padding: 3px 10px; border-radius: 12px;
    font-size: 0.8rem; font-weight: 600; color: #fff; margin: 2px;
  }
</style>
""", unsafe_allow_html=True)


# ── Model loading ─────────────────────────────────────────────────────────────
@st.cache_resource(show_spinner="Loading pose model…")
def load_model(size: str) -> YOLO:
    return YOLO(f"yolov8{size}-pose.pt")


# ── Sidebar ───────────────────────────────────────────────────────────────────
with st.sidebar:
    st.title("⚙️ Settings")
    st.markdown("---")

    model_size = st.radio(
        "Model size",
        options=["n", "s", "m"],
        format_func=lambda x: {"n": "Nano (fastest)", "s": "Small", "m": "Medium (best)"}[x],
        index=0,
    )
    conf_threshold = st.slider("Detection confidence", 0.10, 0.90, 0.30, 0.05)
    show_skeleton = st.checkbox("Show skeleton overlay", value=True)
    privacy_mode = st.checkbox("Privacy mode (blur people)", value=False)

    st.markdown("---")
    st.markdown("**Activity legend**")
    for act in ACTIVITIES:
        r, g, b = ACTIVITY_COLORS[act]
        st.markdown(
            f'<span class="activity-badge" style="background:rgb({r},{g},{b})">{act}</span>',
            unsafe_allow_html=True,
        )


# ── Main ──────────────────────────────────────────────────────────────────────
st.title("🏢 Workplace Activity Analyzer")
st.markdown("Upload a workplace video and the app will detect people and classify what they are doing frame-by-frame.")

uploaded = st.file_uploader(
    "Upload a video file", type=["mp4", "avi", "mov", "webm", "mkv"]
)

if uploaded is None:
    st.info("👆 Upload a video to get started.")
    st.stop()

# ── Process button ────────────────────────────────────────────────────────────
if st.button("▶ Analyse Video", type="primary", use_container_width=True):
    model = load_model(model_size)

    # Save upload to temp file
    suffix = os.path.splitext(uploaded.name)[-1] or ".mp4"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp_in:
        tmp_in.write(uploaded.read())
        input_path = tmp_in.name

    output_path = input_path.replace(suffix, "_annotated.mp4")

    video_info = sv.VideoInfo.from_video_path(input_path)
    total_frames = video_info.total_frames or 1

    tracker = sv.ByteTrack(
        track_activation_threshold=0.25,
        lost_track_buffer=30,
        minimum_matching_threshold=0.8,
        frame_rate=int(video_info.fps or 30),
    )
    classifier = ActivityClassifier(conf_threshold=conf_threshold)

    # Annotators
    box_ann   = sv.BoxAnnotator()
    label_ann = sv.LabelAnnotator(text_scale=0.5, text_thickness=1)
    edge_ann  = sv.EdgeAnnotator(thickness=2)
    vtx_ann   = sv.VertexAnnotator(radius=4)
    blur_ann  = sv.BlurAnnotator(kernel_size=51)

    activity_log: list[dict] = []
    frame_idx = 0
    t_start = time.time()

    status_area = st.empty()
    progress_bar = st.progress(0)

    with sv.VideoSink(output_path, video_info) as sink:
        for frame in sv.get_video_frames_generator(input_path, stride=1):
            frame_idx += 1
            progress = min(frame_idx / total_frames, 1.0)
            progress_bar.progress(progress)

            fps_so_far = frame_idx / max(time.time() - t_start, 0.001)
            status_area.markdown(
                f"Processing frame **{frame_idx}** / {total_frames} &nbsp;·&nbsp; {fps_so_far:.1f} fps"
            )

            # Run model (person class = 0 only)
            results = model(frame, verbose=False, conf=conf_threshold, classes=[0])[0]
            key_points = sv.KeyPoints.from_ultralytics(results)
            detections = sv.Detections.from_ultralytics(results)

            if len(detections) > 0:
                detections = tracker.update_with_detections(detections)
            else:
                detections = tracker.update_with_detections(detections)

            active_ids: set = set()
            labels: list[str] = []
            custom_colors: list = []

            for i in range(len(detections)):
                tid = int(detections.tracker_id[i]) if detections.tracker_id is not None else i
                active_ids.add(tid)

                if i < len(key_points.xy):
                    xy   = key_points.xy[i]
                    conf = key_points.confidence[i] if key_points.confidence is not None else np.ones(17)
                    raw  = classifier.classify(xy, conf)
                else:
                    raw = "Idle"

                classifier.update_history(tid, raw)
                stable = classifier.get_stable_activity(tid)
                labels.append(f"#{tid} {stable}")

                r, g, b = ACTIVITY_COLORS[stable]
                custom_colors.append(sv.Color(r=r, g=g, b=b))

                ts = frame_idx / max(video_info.fps or 30, 1)
                activity_log.append({"frame": frame_idx, "time_sec": round(ts, 2), "person_id": tid, "activity": stable})

            classifier.cleanup_stale_tracks(active_ids)

            # Build annotated frame
            annotated = frame.copy()

            if len(detections) > 0:
                if privacy_mode:
                    annotated = blur_ann.annotate(annotated, detections)

                annotated = box_ann.annotate(annotated, detections)
                annotated = label_ann.annotate(annotated, detections, labels=labels)

                if show_skeleton and len(key_points.xy) > 0:
                    annotated = edge_ann.annotate(annotated, key_points)
                    annotated = vtx_ann.annotate(annotated, key_points)

            sink.write_frame(annotated)

    progress_bar.progress(1.0)
    status_area.success(f"Done — {frame_idx} frames processed in {time.time()-t_start:.1f}s")

    st.session_state["output_path"] = output_path
    st.session_state["activity_log"] = activity_log
    st.session_state["video_info"] = video_info

# ── Results ───────────────────────────────────────────────────────────────────
if "output_path" in st.session_state:
    output_path   = st.session_state["output_path"]
    activity_log  = st.session_state["activity_log"]
    video_info    = st.session_state["video_info"]

    st.markdown("---")
    col_vid, col_stats = st.columns([3, 2], gap="large")

    with col_vid:
        st.subheader("Annotated Video")
        if os.path.exists(output_path):
            with open(output_path, "rb") as f:
                st.video(f.read())
        else:
            st.warning("Annotated video file not found.")

    with col_stats:
        st.subheader("Activity Summary")

        if activity_log:
            df = pd.DataFrame(activity_log)

            # One stable label per person (most frequent)
            person_activity = (
                df.groupby("person_id")["activity"]
                .agg(lambda s: s.value_counts().idxmax())
                .reset_index()
                .rename(columns={"activity": "dominant_activity"})
            )

            total_people = person_activity["person_id"].nunique()
            activity_counts = Counter(person_activity["dominant_activity"])
            top_activity = activity_counts.most_common(1)[0][0] if activity_counts else "—"

            m1, m2 = st.columns(2)
            with m1:
                st.markdown(
                    f'<div class="metric-card"><div class="val">{total_people}</div>'
                    f'<div class="lbl">People detected</div></div>',
                    unsafe_allow_html=True,
                )
            with m2:
                r, g, b = ACTIVITY_COLORS.get(top_activity, (100, 100, 100))
                st.markdown(
                    f'<div class="metric-card"><div class="val" style="color:rgb({r},{g},{b})">'
                    f'{top_activity}</div><div class="lbl">Most common activity</div></div>',
                    unsafe_allow_html=True,
                )

            st.markdown("")

            # Pie chart
            labels_pie  = list(activity_counts.keys())
            values_pie  = list(activity_counts.values())
            colors_pie  = [f"rgb{ACTIVITY_COLORS[a]}" for a in labels_pie]

            fig = go.Figure(go.Pie(
                labels=labels_pie,
                values=values_pie,
                marker=dict(colors=colors_pie),
                hole=0.4,
                textinfo="label+percent",
                hoverinfo="label+value",
            ))
            fig.update_layout(
                showlegend=False,
                margin=dict(t=20, b=20, l=20, r=20),
                paper_bgcolor="rgba(0,0,0,0)",
                plot_bgcolor="rgba(0,0,0,0)",
                font=dict(color="#e0e0e0"),
                height=260,
            )
            st.plotly_chart(fig, use_container_width=True)

            # Per-person table
            st.markdown("**Per-person breakdown**")
            st.dataframe(
                person_activity.rename(columns={"person_id": "Person ID", "dominant_activity": "Activity"}),
                use_container_width=True,
                hide_index=True,
            )

    # Activity log + download
    st.markdown("---")
    st.subheader("Frame-level Activity Log")

    if activity_log:
        df_log = pd.DataFrame(activity_log)
        st.dataframe(df_log, use_container_width=True, hide_index=True, height=300)

        csv_bytes = df_log.to_csv(index=False).encode()
        st.download_button(
            "⬇ Download CSV",
            data=csv_bytes,
            file_name="activity_log.csv",
            mime="text/csv",
        )
