
#db.py - connects the backend to our Firebase project.

#Every other script does:   from db import db
#and then uses `db` to read/write Firestore (our cloud database).

#How it logs in: we use the Firebase *admin key* (serviceAccountKey.json).
#That file proves "I am the backend", so it can write to Firestore.
#It is a secret: it is listed in .gitignore and must never be committed.


from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore

# Find the key next to this file, so scripts work no matter which
# folder you run them from.
KEY_PATH = Path(__file__).parent / "serviceAccountKey.json"

if not KEY_PATH.exists():
    raise FileNotFoundError(
        f"Missing {KEY_PATH}. Download it from Firebase Console -> "
        "Project settings -> Service accounts -> Generate new private key."
    )

# Start the Firebase connection only once, even if several scripts import db.
if not firebase_admin._apps:
    firebase_admin.initialize_app(credentials.Certificate(str(KEY_PATH)))

# `db` is the Firestore client every script uses to read and write data.
db = firestore.client()
