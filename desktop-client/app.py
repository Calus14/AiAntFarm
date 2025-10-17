import threading, time, requests
from flask import Flask, request, jsonify
import tkinter as tk
from tkinter import scrolledtext

API_BASE = "http://localhost:8080"

app = Flask(__name__)
token_cache = {"token": None}

@app.post("/login")
def login():
    data = request.get_json(force=True)
    r = requests.post(f"{API_BASE}/api/v1/auth/dev-token", json={
        "tenantId": data.get("tenantId","tenant-dev"),
        "displayName": data.get("displayName","DesktopUser")
    })
    token_cache["token"] = r.json()["token"]
    return jsonify({"ok": True})

def start_flask():
    app.run(port=5051)

# --- Tkinter UI
def start_ui():
    root = tk.Tk()
    root.title("AI Ant Farm - Desktop")
    frm = tk.Frame(root); frm.pack(padx=10, pady=10)
    room_var = tk.StringVar(value="room-dev")

    tk.Label(frm, text="Room ID").grid(row=0, column=0, sticky="w")
    tk.Entry(frm, textvariable=room_var).grid(row=0, column=1, sticky="ew")

    text = scrolledtext.ScrolledText(frm, width=60, height=20); text.grid(row=1, column=0, columnspan=2, pady=5)
    msg_var = tk.StringVar()

    def login_click():
        requests.post("http://127.0.0.1:5051/login", json={"tenantId":"tenant-dev","displayName":"DesktopUser"})
        text.insert(tk.END, "Logged in (dev token).
")

    def send_click():
        t = token_cache["token"]
        if not t: 
            text.insert(tk.END, "Not logged in.
"); return
        r = requests.post(f"{API_BASE}/api/v1/rooms/{room_var.get()}/messages",
                          headers={"Authorization": f"Bearer {t}"}, json={"text": msg_var.get()})
        if r.status_code < 300:
            text.insert(tk.END, f"Me: {msg_var.get()}
")
            msg_var.set("")

    tk.Button(frm, text="Dev Login", command=login_click).grid(row=2, column=0, sticky="w")
    tk.Entry(frm, textvariable=msg_var, width=50).grid(row=3, column=0, sticky="ew")
    tk.Button(frm, text="Send", command=send_click).grid(row=3, column=1, sticky="e")

    root.mainloop()

if __name__ == "__main__":
    threading.Thread(target=start_flask, daemon=True).start()
    time.sleep(0.3)
    start_ui()
