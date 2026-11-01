package com.volleyball.game.core;

/**
 * Fixed-timestep accumulator ("fix your timestep"). Simulation code always advances by exactly
 * {@link #stepSeconds()}, independent of the render frame rate, which keeps physics deterministic
 * and stable. Rendering may use {@link #alpha()} to interpolate between the last two states.
 *
 * <p>If a frame takes too long (debugger pause, window drag) at most {@code maxStepsPerFrame}
 * steps are run and the remaining backlog is dropped, to avoid a "spiral of death".
 */
public final class FixedStepLoop {
    /** One simulation step of {@code dt} seconds. */
    @FunctionalInterface
    public interface Step {
        void step(float dt);
    }

    private final double stepSeconds;
    private final int maxStepsPerFrame;
    private double accumulator;
    private long totalSteps;
    private double droppedSeconds;

    public FixedStepLoop(int stepsPerSecond, int maxStepsPerFrame) {
        if (stepsPerSecond <= 0) throw new IllegalArgumentException("stepsPerSecond must be positive");
        if (maxStepsPerFrame <= 0) throw new IllegalArgumentException("maxStepsPerFrame must be positive");
        this.stepSeconds = 1.0 / stepsPerSecond;
        this.maxStepsPerFrame = maxStepsPerFrame;
    }

    /** Adds the frame time and runs as many whole steps as fit. Returns the number of steps run. */
    public int advance(float frameSeconds, Step step) {
        if (Float.isFinite(frameSeconds) && frameSeconds > 0f) accumulator += frameSeconds;
        int steps = 0;
        while (accumulator >= stepSeconds && steps < maxStepsPerFrame) {
            step.step((float) stepSeconds);
            accumulator -= stepSeconds;
            steps++;
            totalSteps++;
        }
        if (accumulator >= stepSeconds) {
            double backlog = accumulator - accumulator % stepSeconds;
            droppedSeconds += backlog;
            accumulator -= backlog;
        }
        return steps;
    }

    /** Fraction (0..1) of a step left in the accumulator, for render interpolation. */
    public float alpha() {
        return (float) (accumulator / stepSeconds);
    }

    public float stepSeconds() {
        return (float) stepSeconds;
    }

    public long totalSteps() {
        return totalSteps;
    }

    /** Simulation time skipped because frames were too slow. Useful for profiling. */
    public double droppedSeconds() {
        return droppedSeconds;
    }
}
