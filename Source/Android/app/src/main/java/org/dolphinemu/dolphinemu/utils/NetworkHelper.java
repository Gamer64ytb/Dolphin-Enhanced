package org.dolphinemu.dolphinemu.utils;

import android.app.Service;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;

import androidx.annotation.Keep;

import org.dolphinemu.dolphinemu.DolphinApplication;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkHelper
{
  private static ConnectivityManager GetConnectivityManager()
  {
    Context context = DolphinApplication.getAppContext();
    ConnectivityManager manager =
            (ConnectivityManager) context.getSystemService(Service.CONNECTIVITY_SERVICE);
    if (manager == null)
      Log.warning("Cannot get Network link as ConnectivityManager is null.");
    return manager;
  }

  private static LinkAddress GetIPv4Link()
  {
    ConnectivityManager manager = GetConnectivityManager();
    if (manager == null)
      return null;
    Network active_network = manager.getActiveNetwork();
    if (active_network == null)
    {
      Log.warning("Active network is null.");
      return null;
    }
    LinkProperties properties = manager.getLinkProperties(active_network);
    if (properties == null)
    {
      Log.warning("Link properties is null.");
      return null;
    }
    for (LinkAddress link : properties.getLinkAddresses())
    {
      InetAddress address = link.getAddress();
      if (address instanceof Inet4Address)
        return link;
    }
    Log.warning("No IPv4 link found.");
    return null;
  }

  private static int InetAddressToInt(InetAddress address)
  {
    byte[] net_addr = address.getAddress();
    int result = 0;
    // Convert address to little endian
    for (int i = 0; i < net_addr.length; i++)
    {
      result |= (net_addr[i] & 0xFF) << (8 * i);
    }
    return result;
  }

  @Keep
  public static int GetNetworkIpAddress()
  {
    LinkAddress link = GetIPv4Link();
    if (link == null)
      return 0;
    return InetAddressToInt(link.getAddress());
  }

  @Keep
  public static int GetNetworkPrefixLength()
  {
    LinkAddress link = GetIPv4Link();
    if (link == null)
      return 0;
    return link.getPrefixLength();
  }

  @Keep
  public static int GetNetworkGateway()
  {
    ConnectivityManager manager = GetConnectivityManager();
    if (manager == null)
      return 0;
    Network active_network = manager.getActiveNetwork();
    if (active_network == null)
      return 0;
    LinkProperties properties = manager.getLinkProperties(active_network);
    if (properties == null)
      return 0;
    try
    {
      InetAddress addr_out = InetAddress.getByName("8.8.8.8");
      for (RouteInfo route : properties.getRoutes())
      {
        if (!route.matches(addr_out))
          continue;
        return InetAddressToInt(route.getGateway());
      }
    }
    catch (UnknownHostException ignore)
    {
    }
    Log.warning("No valid gateway found.");
    return 0;
  }
}
