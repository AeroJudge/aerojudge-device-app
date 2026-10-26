package com.aerojudge.judge.dto;

public class CompDTO {

    /**********
     *  This is the comp preferences object.
     *
     *  Here's some of the things we should store here.
     *   - Comp ID.
     *   - Comp Name.
     *   - Scoring Mode:
     *         - global         - as originally coded.   Just score all pilots together regardless of class.
     *         - round          - We choose a round from the list and only those pilots are available for scoring.
     *                            Round number and round type are supplied.
     *   - More to come...
     */

    private int comp_id;
    private String comp_name;
    private String score_mode;
    private int sequences;
    private int unknown_sequences = 0;
    private int judge_id = 1;
    private String sequence_type = "std";
    // Null distinguishes an omitted partial-update value from explicit Off.
    private Boolean attempt_auto_sync_scores;

    public Boolean getAttempt_auto_sync_scores() {
        return attempt_auto_sync_scores;
    }

    public void setAttempt_auto_sync_scores(Boolean attempt_auto_sync_scores) {
        this.attempt_auto_sync_scores = attempt_auto_sync_scores;
    }

    public CompDTO() {
        this.comp_id = 0;
        this.score_mode = null;
        this.comp_name = null;
    }

    public CompDTO(String score_mode, int sequences, int unknown_sequences) {
        this();
        this.score_mode = score_mode;
        this.sequences = sequences;
        this.unknown_sequences = unknown_sequences;
    }


    public int getComp_id() {
        return comp_id;
    }

    public CompDTO setComp_id(int comp_id) {
        this.comp_id = comp_id;
        return this;
    }

    public String getComp_name() {
        return comp_name;
    }
    public void setComp_name(String comp_name) { this.comp_name = comp_name; }
    public String getScore_mode() {
        return score_mode;
    }
    public void setScore_mode(String score_mode) { this.score_mode = score_mode; }
    public int getJudge_id() {
        return judge_id;
    }
    public void setJudge_id(int judge_id) {
        this.judge_id = judge_id;
    }
    public int getUnknown_sequences() {
        return unknown_sequences;
    }
    public void setUnknown_sequences(int unknown_sequences) {
        this.unknown_sequences = unknown_sequences;
    }
    public int getSequences() {
        return sequences;
    }
    public void setSequences(int sequences) {
        this.sequences = sequences;
    }
    public String getSequenceType() {
        return sequence_type;
    }
    public void setSequenceType(String sequence_type) {
        this.sequence_type = sequence_type;
    }
}
