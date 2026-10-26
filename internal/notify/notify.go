package notify

import (
	"fmt"
	"sync"

	"com.comtudia.app/internal/format"
	"com.comtudia.app/internal/plan"
)

// Message is a platform-independent notification payload.
type Message struct {
	Title string
	Body  string
}

// Notifier emits one message per phase change. It is primed on the first
// Update so launching the app does not fire a notification.
type Notifier struct {
	mu     sync.Mutex
	key    string
	primed bool
}

// New returns a ready notifier.
func New() *Notifier {
	return &Notifier{}
}

// Reset primes the notifier again, discarding the previous phase.
func (n *Notifier) Reset() {
	n.mu.Lock()
	defer n.mu.Unlock()
	n.key = ""
	n.primed = false
}

// Update records the state and returns a message when the phase changed.
func (n *Notifier) Update(st plan.State) (Message, bool) {
	n.mu.Lock()
	defer n.mu.Unlock()

	key := st.PhaseKey()
	if !n.primed {
		n.primed = true
		n.key = key
		return Message{}, false
	}
	if key == n.key {
		return Message{}, false
	}
	n.key = key
	return messageFor(st), true
}

func messageFor(st plan.State) Message {
	if st.Phase == plan.PhaseEating {
		if st.FirstMealIndex != st.LastMealIndex {
			return Message{
				Title: "Eating window open",
				Body: fmt.Sprintf("Meals %d–%d until %s",
					st.FirstMealIndex, st.LastMealIndex, format.Clock(st.PhaseEnd)),
			}
		}
		return Message{
			Title: "Eating time",
			Body: fmt.Sprintf("Meal %d of %d until %s",
				st.FirstMealIndex, st.MealCount, format.Clock(st.PhaseEnd)),
		}
	}

	next := "soon"
	if !st.NextEatingPhaseStart.IsZero() {
		next = format.When(st.NextEatingPhaseStart, st.Now)
	}
	return Message{
		Title: "Fasting started",
		Body:  fmt.Sprintf("Next eating %s (in %s)", next, format.Duration(st.Remaining)),
	}
}
