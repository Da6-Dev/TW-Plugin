package br.com.tw.minas.integrations;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class RealMinesBridge {
    private final Object mineManager;
    private final Method getMineWithBlock;
    private final Method findBlockUpdate;

    public RealMinesBridge(Plugin realMines) throws ReflectiveOperationException {
        ClassLoader classLoader = realMines.getClass().getClassLoader();
        Class<?> apiClass = Class.forName("joserodpt.realmines.api.RealMinesAPI", true, classLoader);
        Object api = apiClass.getMethod("getInstance").invoke(null);
        if (api == null) {
            throw new ReflectiveOperationException("RealMinesAPI ainda não foi inicializada.");
        }
        mineManager = apiClass.getMethod("getMineManager").invoke(api);
        if (mineManager == null) {
            throw new ReflectiveOperationException("MineManagerAPI não está disponível.");
        }
        Class<?> managerApiClass = Class.forName(
                "joserodpt.realmines.api.managers.MineManagerAPI", true, classLoader);
        getMineWithBlock = managerApiClass.getMethod("getMineWithBlock", Block.class);
        findBlockUpdate = managerApiClass.getMethod(
                "findBlockUpdate", Player.class, Cancellable.class, Block.class, boolean.class);
    }

    public boolean trackBlockBreak(Player player, Block block) {
        return trackBlockBreak(player, new BlockBreakEvent(block, player), block);
    }

    public boolean trackBlockBreak(Player player, Cancellable event, Block block) {
        try {
            if (getMineWithBlock.invoke(mineManager, block) == null) return false;
            findBlockUpdate.invoke(mineManager, player, event, block, true);
            return true;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Falha ao atualizar o reset da mina RealMines.", e);
        }
    }

    public boolean isMineBlock(Block block) {
        try {
            return getMineWithBlock.invoke(mineManager, block) != null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Falha ao verificar se o bloco pertence a uma mina RealMines.", e);
        }
    }
}
