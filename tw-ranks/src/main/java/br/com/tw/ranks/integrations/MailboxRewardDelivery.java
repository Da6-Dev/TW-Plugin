package br.com.tw.ranks.integrations;

import br.com.tw.ranks.TWRanksPlugin;
import com.rismr1.mailboxgui.api.MailboxGUIApi;
import com.rismr1.mailboxgui.api.request.PackageMailRequest;
import com.rismr1.mailboxgui.api.result.MailboxSendResult;
import com.rismr1.mailboxgui.api.result.MailboxRecipientResult;
import com.rismr1.mailboxgui.api.type.RecipientPolicy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public final class MailboxRewardDelivery {
    private final TWRanksPlugin plugin;
    private final MailboxGUIApi api;

    public MailboxRewardDelivery(TWRanksPlugin plugin, MailboxGUIApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    public boolean send(Player player, String rank, List<ItemStack> rewards) {
        if (rewards.isEmpty()) return true;

        PackageMailRequest.Builder request = PackageMailRequest.builder()
                .recipient(player.getUniqueId(), player.getName())
                .recipientPolicy(RecipientPolicy.CREATE_IF_MISSING)
                .fromDisplayName("TW Network")
                .message("Recompensa por alcançar o rank " + rank + ".")
                .deliveryDelaySeconds(0)
                .sourcePluginName(plugin.getName());
        for (ItemStack reward : rewards) {
            request.addItem(reward.clone());
        }

        MailboxSendResult result = api.sendPackage(request.build());
        if (result.isSuccessful()) {
            player.sendMessage("§aA recompensa do rank " + rank + " foi enviada ao correio. Usa §e/correio§a para recolher.");
            return true;
        }

        String details = result.getRecipientResults().stream()
                .map(MailboxRecipientResult::getMessage)
                .collect(Collectors.joining("; "));
        plugin.getLogger().severe("Não foi possível enviar as recompensas do rank " + rank
                + " para " + player.getName() + " pelo MailboxGUI: " + details);
        return false;
    }
}
