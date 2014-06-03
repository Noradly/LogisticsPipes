package logisticspipes.transport;

import buildcraft.transport.TravelingItem;
import logisticspipes.pipes.PipeItemsInvSysConnector;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;

public class TransportInvConnection extends PipeTransportLogistics {
	
	public TransportInvConnection() {}

	@Override
	protected boolean isItemExitable(ItemIdentifierStack stack) {
		return true;
	}
	
	@Override
	protected void insertedItemStack(ItemIdentifierStack item, ItemRoutingInformation info, TileEntity tile) {
		if(tile instanceof IInventory) {
			((PipeItemsInvSysConnector)this.container.pipe).handleItemEnterInv(item, info, tile);
		}
	}
}
