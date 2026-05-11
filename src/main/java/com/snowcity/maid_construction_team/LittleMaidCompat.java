package com.snowcity.maid_construction_team;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.snowcity.maid_construction_team.api.compat.create.CreateMaidCompat;
import com.snowcity.maid_construction_team.old.event.SchematicBuildMaidEvent;
import com.snowcity.maid_construction_team.old.event.CreateSchematicInteractMaidEvent;
import com.snowcity.maid_construction_team.old.task.SchematicannonTask;
import net.neoforged.neoforge.common.NeoForge;

@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {

    // 默认构造函数，女仆模组会在合适的时间调用这个构造函数。可以在这里注册女仆专属的事件
    // Default constructor, the maid mod will call this constructor at the appropriate time. You can register maid-specific events here.
    public LittleMaidCompat() {
        // 注册 女仆蓝图加农炮
        // 蓝图交互
        NeoForge.EVENT_BUS.register(new CreateSchematicInteractMaidEvent());
        // 蓝图建造
        NeoForge.EVENT_BUS.register(new SchematicBuildMaidEvent());
    }

    /**
     * 添加女仆任务 - 注册新的女仆工作类型
     *
     * @param manager 任务管理器，用来管理所有女仆任务
     */
    @Override
    public void addMaidTask(TaskManager manager) {
        // 添加蓝图加农炮工作模式(Create存在的qingkuangxia)
        if (CreateMaidCompat.init()) {
            manager.add(new SchematicannonTask());
        }
    }
}
