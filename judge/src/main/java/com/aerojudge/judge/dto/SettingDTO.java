package com.aerojudge.judge.dto;

public class SettingDTO {
    //Leave these as defaults for AeroJudge devices
    private int judge_id = 1;
    private String score_host = "192.168.8.100";
    private int score_http_port = 80;
    private int line_number = 1;
    private int score_poll_timeout = 2; //seconds
    private int score_timeout = 10; //seconds
    private String language = "en";
    private int seasonYear = 26;
    
    public SettingDTO() {
    }
    
    public int getLine_number() {
        return line_number;
    }
    public void setLine_number(int line_number) {
        this.line_number = line_number;
    }

    public int getJudge_id() {
        return judge_id;
    }
    public void setJudge_id(int judge_id) {
        this.judge_id = judge_id;
    }

    public String getScore_host() {
        return score_host;
    }
    public void setScore_host(String score_host) {
        this.score_host = score_host;
    }

    public int getScore_http_port() {
        return score_http_port;
    }
    public void setScore_http_port(int score_http_port) {
        this.score_http_port = score_http_port;
    }

    public int getScore_poll_timeout() {
        return score_poll_timeout;
    }
    public void setScore_poll_timeout(int score_poll_timeout) {
        this.score_poll_timeout = score_poll_timeout;
    }

    public int getScore_timeout() {
        return score_timeout;
    }
    public void setScore_timeout(int score_timeout) {
        this.score_timeout = score_timeout;
    }

    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }

    public int getSeasonYear() {
        return seasonYear;
    }
    public void setSeasonYear(int seasonYear) {
        this.seasonYear = seasonYear;
    }

}
