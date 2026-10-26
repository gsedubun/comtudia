namespace Comtudia {

/**
 * A platform independent notification payload.
 */
public class Message : GLib.Object {
    public string title { get; private set; }
    public string body { get; private set; }

    public Message (string title, string body) {
        this.title = title;
        this.body = body;
    }
}

/**
 * Emits one message per phase change. The notifier primes on the first update
 * so launching the app does not fire a notification.
 */
public class Notifier : GLib.Object {
    private string key = "";
    private bool primed = false;

    public Notifier () {
    }

    /**
     * Primes the notifier again, discarding the previous phase.
     */
    public void reset () {
        key = "";
        primed = false;
    }

    /**
     * Records the state and returns a message when the phase changed.
     */
    public Message? update (State state) {
        var next_key = state.phase_key ();
        if (!primed) {
            primed = true;
            key = next_key;
            return null;
        }
        if (next_key == key) {
            return null;
        }
        key = next_key;
        return message_for (state);
    }

    private static Message message_for (State state) {
        if (state.phase == Phase.EATING) {
            if (state.first_meal_index != state.last_meal_index) {
                return new Message (
                    "Eating window open",
                    "Meals %d–%d until %s".printf (
                        state.first_meal_index,
                        state.last_meal_index,
                        Format.clock (state.phase_end)));
            }
            return new Message (
                "Eating time",
                "Meal %d of %d until %s".printf (
                    state.first_meal_index,
                    state.meal_count,
                    Format.clock (state.phase_end)));
        }

        string next = "soon";
        if (state.next_eating_phase_start != null) {
            next = Format.when (state.next_eating_phase_start, state.now);
        }
        return new Message (
            "Fasting started",
            "Next eating %s (in %s)".printf (
                next, Format.duration (state.remaining)));
    }
}

}
