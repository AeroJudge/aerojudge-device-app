package com.aerojudge.judge.dto;

public class RoundDTO {

    /**********************
     * A round looks like this
     *
     *          {                               // Note, these objects are unique by TYPE+CLASS+ROUND_NUM only!!
     *              "round_id": 3               // Their ids are unique within the device.
     *              "type": "KNOWN",            // Can be KNOWN, UNKNOWN, FREESTYLE
     *              "comp_class": "SPORTSMAN",  // Classes.
     *              "round_num": 1,             // What is the comp round num, should be the same accross all devices on line.
     *              "sched_id": "SPO2023s",     // What's the sequence?
     *              "sequences": 2,             // How many seqs to score in this round
     *              "round_start": "2026-05-16T14:15:30Z",
     *              "round_end": "2026-05-16T14:25:30Z"
     *          }
     *
     * Rounds object would look like this:   (See RoundsDTO.java)
     *
     *          {                               // Note, the idea is to plug the rounds property below directly into the comp data monolithic file
     *              "comp_id": 1238             // What comp does this round belong to.
     *              "rounds" [
     *                  { ... round data ... },
     *                  { ... round data ... },
     *                  ...
     *              ]
     *          }
     */

    private Integer round_id = null;
    private String type = null;
    private String comp_class = null;
    private Integer round_num = null;
    private String sched_id = null;
    private String sched_desc = null;
    private int sequences = 1;
    private String round_start = null;
    private Boolean round_start_trusted;
    private String round_end = null;
    private Boolean round_end_trusted;
    public Boolean getRound_start_trusted() { return round_start_trusted; }
    public void setRound_start_trusted(Boolean value) { round_start_trusted = value; }
    public Boolean getRound_end_trusted() { return round_end_trusted; }
    public void setRound_end_trusted(Boolean value) { round_end_trusted = value; }

    public RoundDTO() {
    }

    public RoundDTO(Integer round_id) {
        this();
        this.round_id = round_id;
    }

    public Integer getRound_id() {
        return round_id;
    }
    public void setRound_id(Integer round_id) { this.round_id = round_id; }
    public String getType() {
        return type;
    }
    public void setType(String type) { this.type = type; }
    public String getComp_class() {
        return comp_class;
    }
    public void setComp_class(String comp_class) { this.comp_class = comp_class; }
    public Integer getRound_num() {
        return round_num;
    }
    public void setRound_num(Integer round_num) { this.round_num = round_num; }
    public String getSched_id() {
        return sched_id;
    }
    public void setSched_id(String sched_id) { this.sched_id = sched_id; }
    public String getSched_desc() { return sched_desc; }
    public void setSched_desc(String sched_desc) { this.sched_desc = sched_desc; }
    public int getSequences() {
        return sequences;
    }
    public void setSequences(int sequences) { this.sequences = sequences; }
    public String getRound_start() { return round_start; }
    public void setRound_start(String round_start) { this.round_start = round_start; }
    public String getRound_end() { return round_end; }
    public void setRound_end(String round_end) { this.round_end = round_end; }
}
