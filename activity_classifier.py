from collections import defaultdict, deque
import numpy as np


ACTIVITIES = ["Working", "On Phone", "Sleeping", "Idle"]

ACTIVITY_COLORS = {
    "Working":  (0, 200, 0),
    "On Phone": (0, 140, 255),
    "Sleeping": (0, 0, 220),
    "Idle":     (160, 160, 160),
}

# COCO keypoint indices
KP = {
    "nose": 0,
    "l_shoulder": 5, "r_shoulder": 6,
    "l_elbow": 7,    "r_elbow": 8,
    "l_wrist": 9,    "r_wrist": 10,
    "l_hip": 11,     "r_hip": 12,
}


class ActivityClassifier:
    def __init__(self, history_size: int = 30, conf_threshold: float = 0.3):
        self.history_size = history_size
        self.conf_threshold = conf_threshold
        self.history: dict = defaultdict(lambda: deque(maxlen=history_size))

    def _get(self, xy, conf, name):
        idx = KP[name]
        if conf[idx] >= self.conf_threshold:
            return xy[idx]
        return None

    def classify(self, xy: np.ndarray, conf: np.ndarray) -> str:
        g = lambda name: self._get(xy, conf, name)

        nose      = g("nose")
        l_sh      = g("l_shoulder")
        r_sh      = g("r_shoulder")
        l_wrist   = g("l_wrist")
        r_wrist   = g("r_wrist")
        l_elbow   = g("l_elbow")
        r_elbow   = g("r_elbow")
        l_hip     = g("l_hip")
        r_hip     = g("r_hip")

        # Need at least one shoulder to do anything useful
        if l_sh is None and r_sh is None:
            return "Idle"

        sh_pts = [p for p in [l_sh, r_sh] if p is not None]
        sh_mid_y = float(np.mean([p[1] for p in sh_pts]))
        sh_mid_x = float(np.mean([p[0] for p in sh_pts]))

        if l_sh is not None and r_sh is not None:
            sh_width = abs(float(l_sh[0]) - float(r_sh[0])) + 1e-6
        else:
            sh_width = 60.0  # fallback pixels

        # --- Sleeping: head at or below shoulder level ---
        if nose is not None and nose[1] >= sh_mid_y - 15:
            return "Sleeping"

        # --- On Phone: either wrist close to nose ---
        if nose is not None:
            for wrist in [l_wrist, r_wrist]:
                if wrist is not None:
                    dist = float(np.linalg.norm(wrist - nose))
                    if dist < sh_width * 0.85:
                        return "On Phone"

        # --- Working: wrists at desk level, head upright ---
        score = 0.0

        if nose is not None and nose[1] < sh_mid_y - 10:
            score += 1.0  # head upright

        hip_y = None
        if l_hip is not None and r_hip is not None:
            hip_y = (float(l_hip[1]) + float(r_hip[1])) / 2
        elif l_hip is not None:
            hip_y = float(l_hip[1])
        elif r_hip is not None:
            hip_y = float(r_hip[1])

        for wrist, elbow in [(l_wrist, l_elbow), (r_wrist, r_elbow)]:
            if wrist is not None:
                wy = float(wrist[1])
                # Wrist between shoulder and hip (desk height zone)
                if hip_y is not None and sh_mid_y < wy < hip_y:
                    score += 1.0
                elif wy > sh_mid_y:
                    score += 0.4
                # Elbow bent: elbow and wrist at similar height
                if elbow is not None:
                    bend = abs(float(elbow[1]) - wy)
                    if bend < sh_width * 0.6:
                        score += 0.5

        if score >= 1.5:
            return "Working"

        return "Idle"

    def update_history(self, tracker_id: int, activity: str):
        self.history[tracker_id].append(activity)

    def get_stable_activity(self, tracker_id: int) -> str:
        buf = list(self.history[tracker_id])
        if not buf:
            return "Idle"
        return max(set(buf), key=buf.count)

    def reset(self):
        self.history.clear()

    def cleanup_stale_tracks(self, active_ids: set):
        stale = [tid for tid in list(self.history.keys()) if tid not in active_ids]
        for tid in stale:
            del self.history[tid]
