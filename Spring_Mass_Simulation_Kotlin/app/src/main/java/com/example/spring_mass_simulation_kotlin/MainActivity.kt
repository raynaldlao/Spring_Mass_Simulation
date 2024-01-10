/* The application can be paused and reset. It is possible for
the user to pause and be able to move the SeekBar cursor (and thus the spring)
then press the "Play" button. By moving the spring, its force and thus its bounce also changes.
*/

package com.example.spring_mass_simulation_kotlin

import android.os.Bundle
import android.view.animation.Interpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout.LayoutParams
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import android.media.MediaPlayer

class MainActivity : AppCompatActivity() {

    // Declaration of view elements
    private lateinit var mSeekBar: SeekBar
    private lateinit var mSpring: ImageView
    private lateinit var leftArrow: ImageView
    private lateinit var rightArrow: ImageView
    private lateinit var mPositionTextView: TextView
    private lateinit var resetButton: Button
    private lateinit var pauseButton: Button

    companion object {
        private const val START_POSITION = 0 // Starting position of the spring and is initialized with the value 0
        private const val MIN_SPRING_WIDTH = 75 // Minimum width of the spring
        private const val MAX_SPRING_WIDTH = 825 // Maximum width of the spring (default: 700 for WXGA (Tablet) and 825 Pixel 2 (Phone))
        private const val ARROW_WIDTH_FACTOR = 5 // Width of the arrows, if the value is large, the size of the arrows is large and vice versa
        private const val SEEK_BAR_MAX = 40 // The max value of the SeekBar because -20 to 20 so 40
        private const val SEEK_BAR_INITIAL_PROGRESS = 20 // The initial value of the SeekBar because the seekbar is 40 where 20 is the half
        private const val DURATION_IN_MILLIS = 100000L  // Duration of the animation in milliseconds
        private const val BOUNCE_DURATION_FACTOR = 2 // Bounce duration of the animation which should be 2 by default
        private const val STIFFNESS = 1.5f // Stiffness of the spring at 1.5f by default, the more the stiffness is increased, the less the number of oscillation
    }

    private var bounceJob: Job? = null
    private var isAnimationPaused = false
    private var elapsedTime = 0L // Declaration of the variable elapsedTime of type Long and initialized to 0. This variable is used to store the elapsed time during the bounce animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // You can change this file if you want to adapt the format

        // Initialization of ids in xml
        mSeekBar = findViewById(R.id.position_seekBar)
        mSpring = findViewById(R.id.spring)
        leftArrow = findViewById(R.id.left_arrow)
        rightArrow = findViewById(R.id.right_arrow)
        mPositionTextView = findViewById(R.id.txtv_position_label)
        resetButton = findViewById(R.id.reset_button)
        pauseButton = findViewById(R.id.pause_button)

        mSeekBar.max = SEEK_BAR_MAX
        mSeekBar.progress = SEEK_BAR_INITIAL_PROGRESS

        SpringUpdate(START_POSITION)
        mPositionTextView.text = "$START_POSITION cm"

        // Listener for SeekBar progress change
        mSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Update the spring image and position text based on progress
                SpringUpdate(progress - (SEEK_BAR_MAX / 2))
                mPositionTextView.text = "${progress - (SEEK_BAR_MAX / 2)} cm"
            }


            override fun onStartTrackingTouch(seekBar: SeekBar) {
                CancelBounceAnimation() // Cancel the ongoing animation when we start touching the SeekBar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // When we stop touching the SeekBar, we determine the target progress and start the bounce animation
                val targetProgress = SEEK_BAR_INITIAL_PROGRESS
                val currentProgress = seekBar.progress
                val distance = Math.abs(currentProgress - targetProgress)
                val bounces = distance
                val stiffness = STIFFNESS

                BeginBounceAnimation(currentProgress, targetProgress, bounces, stiffness)
            }
        })

        // Listener for the reset button
        resetButton.setOnClickListener {
            val clickSound: MediaPlayer = MediaPlayer.create(this, R.raw.button_click) // Create a MediaPlayer and load your audio file
            clickSound.start() // Play the sound
            CancelBounceAnimation() // Cancel the ongoing animation
            mSeekBar.progress = SEEK_BAR_INITIAL_PROGRESS
            SpringUpdate(START_POSITION)
            mPositionTextView.text = "$START_POSITION cm"
            elapsedTime = 0L // Elapsed time is reset to 0 during reset
        }

        // Listener for the pause button
        pauseButton.setOnClickListener {
            val clickSound: MediaPlayer = MediaPlayer.create(this, R.raw.button_click)
            clickSound.start()
            if (isAnimationPaused) {
                pauseButton.text = "Pause"
                isAnimationPaused = false
                ResumeAnimation() // Resume the ongoing animation
            } else {
                pauseButton.text = "Play"
                isAnimationPaused = true
                PauseAnimation() // Pause the ongoing animation
            }
        }

    }

    override fun onStop() {
        super.onStop()
        CancelBounceAnimation() // Cancel the ongoing animation when the activity is stopped

        // Reset the state of the pause button
        pauseButton.text = "Pause"
        isAnimationPaused = false

        // Reset the state of the SeekBar and position text
        mSeekBar.progress = SEEK_BAR_INITIAL_PROGRESS
        SpringUpdate(START_POSITION)
        mPositionTextView.text = "$START_POSITION cm"
        elapsedTime = 0L // The elapsed time is reset to 0 when resetting
    }

    // Method to update the spring image and arrows based on the SeekBar progress
    private fun SpringUpdate(progress: Int) {
        // Initialized variables that allow the display of arrows in real time and depending on their sizes (force exerted if we lengthen or compress the spring)
        var LeftArrowWidth = (progress - START_POSITION) * ARROW_WIDTH_FACTOR
        var RightArrowWidth = (START_POSITION - progress) * ARROW_WIDTH_FACTOR

        // Condition to avoid the appearance of the left and right arrow
        if (progress >= START_POSITION) {
            RightArrowWidth = 0
        } else {
            LeftArrowWidth = 0
        }

        val layoutParamsLeftArrow = leftArrow.layoutParams as LayoutParams // Retrieval of the layout parameters of the left arrow image
        layoutParamsLeftArrow.width = LeftArrowWidth.coerceAtLeast(0) // Update the width of the image ensuring it is non-negative
        leftArrow.layoutParams = layoutParamsLeftArrow // Update the layout parameters of the image

        val layoutParamsRightArrow = rightArrow.layoutParams as LayoutParams // Retrieval of the layout parameters of the right arrow image
        layoutParamsRightArrow.width = RightArrowWidth.coerceAtLeast(0)
        rightArrow.layoutParams = layoutParamsRightArrow

        val newWidth = (((progress + (SEEK_BAR_MAX / 2)) / SEEK_BAR_MAX.toDouble()) * (MAX_SPRING_WIDTH - MIN_SPRING_WIDTH)).toInt() + MIN_SPRING_WIDTH // Calculation of the new width of the spring based on the progress
        val params = mSpring.layoutParams as LayoutParams // Retrieval of the layout parameters of the spring
        params.width = newWidth.coerceAtLeast(0)
        mSpring.layoutParams = params
    }

    // Method to start the bounce animation
    private fun BeginBounceAnimation(
        currentProgress: Int,
        targetProgress: Int,
        bounces: Int,
        stiffness: Float,
    ) {
        CancelBounceAnimation() // Cancel the ongoing animation

        elapsedTime = 0L // Reset elapsed time
        bounceJob = CoroutineScope(Dispatchers.Main).launch { // Launch a coroutine on the main thread
            val duration = DURATION_IN_MILLIS / BOUNCE_DURATION_FACTOR // Calculate the duration of the animation based on the bounce duration factor
            val interpolator = BounceInterpolator(bounces, stiffness) // Create a custom interpolator called for the bounce effect

            while (isActive && elapsedTime < duration) { // Loop as long as the coroutine is active and the elapsed time is less than the duration
                if (!isAnimationPaused) { // If the animation is not paused
                    // Calculate the progress based on the elapsed time and interpolation
                    val progress =
                        (currentProgress - ((currentProgress - targetProgress) * interpolator.getInterpolation(
                            elapsedTime.toFloat() / duration
                        ))).toInt()
                    mSeekBar.progress = progress // Update the SeekBar progress
                    elapsedTime += 50 // Increment elapsed time
                }
                delay(50) // Pause for 50ms between each update
            }
            mSeekBar.progress = targetProgress // Set the final progress of the SeekBar
        }
    }

    // Method to cancel the bounce animation
    private fun CancelBounceAnimation() {
        bounceJob?.cancel()
        bounceJob = null
        elapsedTime = 0L
    }

    // Method to pause the animation
    private fun PauseAnimation() {
        isAnimationPaused = true
    }

    // Method to resume the animation
    private fun ResumeAnimation() {
        isAnimationPaused = false
    }
}

// Custom Interpolator class for the bounce effect
class BounceInterpolator(private val bounces: Int, private val stiffness: Float) : Interpolator {
    override fun getInterpolation(t: Float): Float {
        // Calculate the interpolation based on the bounce effect
        return (-Math.pow(2.0, -10.0 * t.toDouble()) * Math.sin((t - 0.125f / bounces) * (2.0 * Math.PI) / (0.25f / bounces) * stiffness) + 1).toFloat()
    }
}
