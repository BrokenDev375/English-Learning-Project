package vn.edu.ptit.english.enums;

public final class AttemptEnums {
    private AttemptEnums() {}
    public enum Mode { LEARN, PRACTICE, REVIEW }
    public enum State { IN_PROGRESS, SUBMITTED }
    public enum PracticeRole { GUIDED, INDEPENDENT, REVIEW_VARIANT }
    public enum TrialKind { FIRST, GUIDED_RETRY, CORRECTION }
    public enum TrialSource { CHECK, SUBMIT, RETRY }
    public enum Outcome { CORRECT, INCORRECT, UNANSWERED }
    /** NOT_STARTED is a read projection; only STARTED/COMPLETED are stored. */
    public enum ProgressState { NOT_STARTED, STARTED, COMPLETED }
}
