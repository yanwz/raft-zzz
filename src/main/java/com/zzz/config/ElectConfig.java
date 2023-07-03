package com.zzz.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Random;

@Getter
@Setter
public class ElectConfig {

    private int electInterval;

    private int electRandomRound;


    public long electInterval(){
        return this.electInterval;
    }

    public long timeout(){
        return this.electInterval * 3L;
    }
    public long randomTimeout(){
        return timeout() + new Random().nextInt(electRandomRound);
    }

}
