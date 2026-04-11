Verbus custom sound sets
=========================

Put each sound pack in its own folder:

assets/soundsets/<set_id>/

Example:
assets/soundsets/retro/
assets/soundsets/memes/

The app auto-detects all folders inside assets/soundsets and shows them in Options > Sounds.

Supported filenames per event (first matching file wins):
- tap.*
- single_tap.*
- double_tap.*
- button_press.*
- topic_success.*
- topic_skip.*
- topic_timeout.*
- round_success.*
- round_failure.*

Supported extensions:
- ogg
- mp3
- wav
- m4a

You can omit some files. Missing sounds automatically fall back to the built-in procedural beeps.
