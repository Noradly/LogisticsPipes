package logisticspipes.transport;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import logisticspipes.interfaces.routing.IRequireReliableFluidTransport;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.IRouterManager;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.routing.order.IDistanceTracker;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.LPPosition;
import logisticspipes.utils.tuples.Pair;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

public abstract class LPTravelingItem {

	public static final Map<Integer, WeakReference<LPTravelingItemServer>> serverList = new HashMap<Integer, WeakReference<LPTravelingItemServer>>();
	public static final Map<Integer, WeakReference<LPTravelingItemClient>> clientList = new HashMap<Integer, WeakReference<LPTravelingItemClient>>();
	public static final List<Pair<Integer, Object>> forceKeep = new ArrayList<Pair<Integer,Object>>();
	public static final BitSet clientSideKnownIDs = new BitSet();
	
	private static int nextFreeId = 0;
	protected int id;
	protected float speed = 0.01F;
	
	public int lastTicked = 0;
	
	protected TileEntity container;
	protected float position = 0;
	public ForgeDirection input = ForgeDirection.UNKNOWN;
	public ForgeDirection output = ForgeDirection.UNKNOWN;
	public final EnumSet<ForgeDirection> blacklist = EnumSet.noneOf(ForgeDirection.class);

	public LPTravelingItem() {
		this.id = getNextId();
	}

	public LPTravelingItem(int id, float position, ForgeDirection input, ForgeDirection output) {
		this.id = id;
		this.position = position;
		this.input = input;
		this.output = output;
	}
	
	public LPTravelingItem(int id) {
		this.id = id;
	}

	protected int getNextId() {
		return ++nextFreeId;
	}

	public void setPosition(float position) {
		this.position = position;
	}
	
	public float getPosition() {
		return this.position;
	}

	public float getSpeed() {
		return speed;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	public void setContainer(TileEntity container) {
		this.container = container;
	}

	public TileEntity getContainer() {
		return container;
	}
	
	public int getId() {
		return id;
	}

	public abstract ItemIdentifierStack getItemIdentifierStack();

	public boolean isCorrupted() {
		return false;
	}
	
	public static final class LPTravelingItemClient extends LPTravelingItem {
		@Setter
		private ItemIdentifierStack item;
		public LPTravelingItemClient(int id, float position, ForgeDirection input, ForgeDirection output) {
			super(id, position, input, output);
		}

		public LPTravelingItemClient(int id, ItemIdentifierStack stack) {
			super(id);
			this.item = stack;
		}

		@Override
		public ItemIdentifierStack getItemIdentifierStack() {
			return item;
		}
		
		public void updateInformation(ForgeDirection input, ForgeDirection output, float speed, float position) {
			this.input = input;
			this.output = output;
			this.speed = speed;
			if(Math.abs(position - this.position) > 0.3F && Math.abs((position + 1.0F) - this.position) > 0.3F) {
				this.position = position;
			}
		}
	}
	
	public static final class LPTravelingItemServer extends LPTravelingItem implements IRoutedItem {
		@Getter
		private ItemRoutingInformation info;

		public LPTravelingItemServer(ItemIdentifierStack stack) {
			super();
			info = new ItemRoutingInformation();
			info.setItem(stack);
		}
		
		public LPTravelingItemServer(ItemRoutingInformation info) {
			super();
			this.info = info;
		}
		
		public LPTravelingItemServer(NBTTagCompound data) {
			super();
			info = new ItemRoutingInformation();
			this.readFromNBT(data);
		}
		
		@Override
		public ItemIdentifierStack getItemIdentifierStack() {
			return info.getItem();
		}
		
		public void setInformation(ItemRoutingInformation info) {
			this.info = info;
		}

		public void readFromNBT(NBTTagCompound data) {
			setPosition(data.getFloat("position"));
			setSpeed(data.getFloat("speed"));
			input = ForgeDirection.getOrientation(data.getInteger("input"));
			output = ForgeDirection.getOrientation(data.getInteger("output"));
			info.readFromNBT(data);
		}

		public void writeToNBT(NBTTagCompound data) {
			data.setFloat("position", getPosition());
			data.setFloat("speed", getSpeed());
			data.setInteger("input", input.ordinal());
			data.setInteger("output", output.ordinal());
			info.writeToNBT(data);
		}

		public EntityItem toEntityItem() {
			World worldObj = container.getWorldObj();
			if (MainProxy.isServer(worldObj)) {
				if (getItemIdentifierStack().getStackSize() <= 0) {
					return null;
				}

				if(getItemIdentifierStack().makeNormalStack().getItem() instanceof LogisticsFluidContainer) {
					itemDroped();
					return null;
				}
				
				int xCoord = container.xCoord;
				int yCoord = container.yCoord;
				int zCoord = container.zCoord;
				//N, W and down need to move a tiny bit beyond the block end because vanilla uses floor(coord) to determine block x/y/z
				if(output == ForgeDirection.DOWN) {
					//position.moveForwards(0.251);
					yCoord -= 0.251;
				} else if(output == ForgeDirection.UP) {
					//position.moveForwards(0.75);
					yCoord += 0.75;
				} else if(output == ForgeDirection.NORTH) {
					//position.moveForwards(0.501);
					zCoord -= 0.501;
				} else if(output == ForgeDirection.WEST) {
					//position.moveForwards(0.501);
					xCoord -= 0.501;
				} else if(output == ForgeDirection.SOUTH) {
					//position.moveForwards(0.5);
					zCoord += 0.5;
				} else if(output == ForgeDirection.EAST) {
					//position.moveForwards(0.5);
					xCoord += 0.5;
				}

				LPPosition motion = new LPPosition(0, 0, 0);
				motion.moveForward(output, 0.1 + getSpeed() * 2F);

				EntityItem entityitem = new EntityItem(worldObj, xCoord, yCoord, zCoord, getItemIdentifierStack().makeNormalStack());

				//entityitem.lifespan = 1200;
				//entityitem.delayBeforeCanPickup = 10;

				float f3 = worldObj.rand.nextFloat() * 0.01F - 0.02F;
				entityitem.motionX = (float) worldObj.rand.nextGaussian() * f3 + motion.getXD();
				entityitem.motionY = (float) worldObj.rand.nextGaussian() * f3 + motion.getYD();
				entityitem.motionZ = (float) worldObj.rand.nextGaussian() * f3 + motion.getZD();
				itemDroped();

				return entityitem;
			} else {
				return null;
			}
		}

		public boolean isCorrupted() {
			return getItemIdentifierStack() == null || getItemIdentifierStack().getStackSize() <= 0;
		}
		
		protected void itemDroped() {}

		@Override
		public void clearDestination() {
			if (info.destinationint >= 0) {
				itemWasLost();
				info.jamlist.add(info.destinationint);
			}
			//keep buffercounter and jamlist
			info.destinationint = -1;
			info.destinationUUID = null;
			info._doNotBuffer = false;
			info.arrived = false;
			info._transportMode = TransportMode.Unknown;
		}
		
		public void itemWasLost() {
			if(this.container != null) {
				if(MainProxy.isClient(this.container.getWorldObj())) return;
			}
			if (info.destinationint >= 0 && SimpleServiceLocator.routerManager.isRouter(info.destinationint)){
				IRouter destinationRouter = SimpleServiceLocator.routerManager.getRouter(info.destinationint); 
				if (destinationRouter.getPipe() != null && destinationRouter.getPipe() instanceof IRequireReliableTransport) {
					((IRequireReliableTransport)destinationRouter.getPipe()).itemLost(info.getItem().clone());
				}
				if (destinationRouter.getPipe() != null && destinationRouter.getPipe() instanceof IRequireReliableFluidTransport) {
					if(info.getItem().getItem().isFluidContainer()) {
						FluidStack liquid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(info.getItem());
						((IRequireReliableFluidTransport)destinationRouter.getPipe()).liquidLost(FluidIdentifier.get(liquid), liquid.amount);
					}
				}
			}
		}

		@Override
		public int getDestination() {
			return info.destinationint;
		}

		@Override
		public void setDestination(int destination) {
			info.destinationint = destination;
			IRouter router = SimpleServiceLocator.routerManager.getRouter(destination);
			if(router != null) {
				info.destinationUUID = router.getId();
			} else {
				info.destinationUUID = null;
			}
		}

		@Override
		public void setDoNotBuffer(boolean isBuffered) {
			info._doNotBuffer = isBuffered;
		}

		@Override
		public boolean getDoNotBuffer() {
			return info._doNotBuffer;
		}

		@Override
		public void setArrived(boolean flag) {
			info.arrived = flag;
		}

		@Override
		public boolean getArrived() {
			return info.arrived;
		}

		@Override
		public void split(int itemsToTake, ForgeDirection orientation) {
			if(getItemIdentifierStack().getItem().isFluidContainer()) {
				throw new UnsupportedOperationException("Can't split up a FluidContainer");
			}
			ItemIdentifierStack stackToKeep = this.getItemIdentifierStack();
			ItemIdentifierStack stackToSend = stackToKeep.clone();
			stackToKeep.setStackSize(itemsToTake);
			stackToSend.setStackSize(stackToSend.getStackSize() - itemsToTake);
			
			this.id = this.getNextId();
			
			LPTravelingItemServer newItem = new LPTravelingItemServer(stackToSend);
			newItem.setSpeed(getSpeed());
			newItem.setTransportMode(getTransportMode());
			
			if (this.container instanceof LogisticsTileGenericPipe && ((LogisticsTileGenericPipe)this.container).pipe.transport instanceof PipeTransportLogistics) {
				((PipeTransportLogistics)((LogisticsTileGenericPipe)this.container).pipe.transport).injectItem((LPTravelingItem)newItem, orientation);
			}
		}
		
		@Override
		public void setTransportMode(TransportMode transportMode) {
			info._transportMode = transportMode;
		}

		@Override
		public TransportMode getTransportMode() {
			return info._transportMode;
		}

		@Override
		public void addToJamList(IRouter router) {
			info.jamlist.add(router.getSimpleID());
		}

		@Override
		public List<Integer> getJamList() {
			return info.jamlist;
		}

		@Override
		public int getBufferCounter() {
			return info.bufferCounter;
		}

		@Override
		public void setBufferCounter(int counter) {
			info.bufferCounter = counter;
		}

		@Override
		public UUID getDestinationUUID() {
			return info.destinationUUID;
		}

		@Override
		public void checkIDFromUUID() {	
			IRouterManager rm = SimpleServiceLocator.routerManager;
			IRouter router = rm.getRouter(info.destinationint);
			if(router == null || info.destinationUUID != router.getId()) {
				info.destinationint = rm.getIDforUUID(info.destinationUUID);
			}
		}

		public void refreshDestinationInformation() {
			IRouter destinationRouter = SimpleServiceLocator.routerManager.getRouter(info.destinationint); 
			if (destinationRouter != null && destinationRouter.getPipe() instanceof CoreRoutedPipe){
				((CoreRoutedPipe) destinationRouter.getPipe()).refreshItem(this.getInfo());
			}
		}

		@Override
		public void setDistanceTracker(IDistanceTracker tracker) {
			info.tracker = tracker;
		}

		@Override
		public IDistanceTracker getDistanceTracker() {
			return info.tracker;
		}

		public void resetDelay() {
			info.resetDelay();
		}
	}
}
