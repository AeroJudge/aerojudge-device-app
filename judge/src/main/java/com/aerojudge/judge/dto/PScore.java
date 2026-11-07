package com.aerojudge.judge.dto;

public class PScore {
    private int round;
    private int sequence;
    private float[] scores;
    private String type;

    private String seq_start;
    public String getSeq_start() { return seq_start; }
    public void setSeq_start(String value) { seq_start = value; }
    private String seq_end;
    public String getSeq_end() { return seq_end; }
    public void setSeq_end(String value) { seq_end = value; }
    private Boolean seq_time_trusted;
    public Boolean getSeq_time_trusted() { return seq_time_trusted; }
    public void setSeq_time_trusted(Boolean value) { seq_time_trusted = value; }

    
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public PScore(int round, int sequence, float[] scores, String type) {
        this.round = round;
        this.sequence = sequence;
        this.scores = scores;
        this.type = type;
    }
    public int getRound() {
        return round;
    }
    public void setRound(int round) {
        this.round = round;
    }
    public int getSequence() {
        return sequence;
    }
    public void setSequence(int sequence) {
        this.sequence = sequence;
    }
    public float[] getScores() {
        return scores;
    }
    public void setScores(float[] scores) {
        this.scores = scores;
    }
    
}
