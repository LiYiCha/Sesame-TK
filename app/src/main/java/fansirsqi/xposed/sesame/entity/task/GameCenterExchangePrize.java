package fansirsqi.xposed.sesame.entity.task;

import lombok.Data;

/**
 * @author yicha
 * 游戏中心查询兑换项
 */
@Data
public class GameCenterExchangePrize {
    private String campId;
    private String prizeId;
    private String prizeName;
    private int consumePointAmount;
    private String prizeType;

    public GameCenterExchangePrize(String campId, String prizeId, String prizeName, int consumePointAmount, String prizeType) {
        this.campId = campId;
        this.prizeId = prizeId;
        this.prizeName = prizeName;
        this.consumePointAmount = consumePointAmount;
        this.prizeType = prizeType;
    }

    // Getter methods
    public String getCampId() { return campId; }
    public String getPrizeId() { return prizeId; }
    public String getPrizeName() { return prizeName; }
    public int getConsumePointAmount() { return consumePointAmount; }
    public String getPrizeType() { return prizeType; }
}