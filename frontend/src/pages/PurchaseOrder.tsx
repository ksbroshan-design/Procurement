import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { listPurchaseOrders, getPurchaseOrder } from '../api/purchaseOrder';
import { PurchaseOrder as PurchaseOrderType } from '../types';
import { formatCurrency, formatDate } from '../utils/format';
import {
  ShoppingBag,
  CheckCircle2,
  Printer,
  ArrowLeft,
  RefreshCw,
} from 'lucide-react';

export const PurchaseOrder: React.FC = () => {
  const { id } = useParams<{ id: string }>();

  const [orders, setOrders] = useState<PurchaseOrderType[]>([]);
  const [selectedOrder, setSelectedOrder] = useState<PurchaseOrderType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      setError(null);
      try {
        if (id) {
          const po = await getPurchaseOrder(id);
          setSelectedOrder(po);
        } else {
          const list = await listPurchaseOrders();
          if (Array.isArray(list)) {
            setOrders(list);
          }
        }
      } catch (err: any) {
        setError(err.message || 'Failed to retrieve purchase order.');
      } finally {
        setIsLoading(false);
      }
    };
    fetchData();
  }, [id]);

  const handlePrint = () => {
    window.print();
  };

  // 1. Single Purchase Order Document View
  if (id || selectedOrder) {
    if (isLoading) {
      return (
        <div className="p-12 text-center text-xs text-slate-400 space-y-2">
          <div className="w-6 h-6 border-2 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto"></div>
          <p>Loading confirmed purchase order document...</p>
        </div>
      );
    }

    if (!selectedOrder) {
      return (
        <div className="glass-panel p-8 rounded-xl border border-rose-500/30 text-center max-w-md mx-auto space-y-4">
          <p className="text-xs text-rose-400">No Purchase Order record found for ID: {id}</p>
          <Link
            to="/purchase-orders"
            className="inline-flex items-center space-x-2 px-4 py-2 bg-slate-900 text-slate-200 border border-slate-800 rounded-lg text-xs font-semibold"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>All Purchase Orders</span>
          </Link>
        </div>
      );
    }

    return (
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="flex justify-between items-center print:hidden">
          <Link
            to={id ? `/procurements/${id}` : '/purchase-orders'}
            className="text-xs text-slate-400 hover:text-slate-200 flex items-center space-x-1 font-semibold"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>{id ? 'Back to Procurement Details' : 'Back to Purchase Orders'}</span>
          </Link>

          <button
            onClick={handlePrint}
            className="px-4 py-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-800 text-xs font-semibold flex items-center space-x-2 transition"
          >
            <Printer className="w-3.5 h-3.5" />
            <span>Print Invoice Document</span>
          </button>
        </div>

        {/* Corporate PO Invoice Document */}
        <div className="glass-panel p-8 sm:p-12 rounded-2xl border border-slate-800 bg-slate-900/90 text-slate-100 shadow-2xl space-y-8 print:bg-white print:text-black print:p-0 print:border-none">
          {/* Header */}
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-800 pb-6 print:border-gray-300">
            <div>
              <div className="flex items-center space-x-2">
                <div className="w-8 h-8 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">
                  <ShoppingBag className="w-4 h-4" />
                </div>
                <h2 className="text-2xl font-black tracking-tight">PURCHASE ORDER</h2>
              </div>
              <p className="text-xs text-slate-400 font-mono mt-1">
                PO REF: {selectedOrder.id ? selectedOrder.id.toUpperCase() : 'PO-CONFIRMED'}
              </p>
            </div>

            <div className="text-right">
              <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 print:border-green-600 print:text-green-700">
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>CONFIRMED & ISSUED</span>
              </span>
              <p className="text-[11px] text-slate-400 mt-1">
                Confirmed: {formatDate(selectedOrder.confirmedAt || selectedOrder.createdAt)}
              </p>
            </div>
          </div>

          {/* Parties Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-8 text-xs">
            <div className="space-y-1.5">
              <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                Issued By (Buyer):
              </span>
              <p className="font-bold text-slate-100 text-sm">Autonomous Procurement Engine Corp</p>
              <p className="text-slate-400">Enterprise Procurement Operations</p>
              <p className="text-slate-400 font-mono text-[11px]">
                Procurement Link: #{selectedOrder.procurementId?.substring(0, 8)}
              </p>
            </div>

            <div className="space-y-1.5 sm:text-right">
              <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                Vendor Supplier:
              </span>
              <p className="font-bold text-slate-100 text-sm">{selectedOrder.vendorName}</p>
              <p className="text-slate-400">Registered Vendor Catalog Partner</p>
              <p className="text-slate-400 font-mono text-[11px]">
                Vendor ID: {selectedOrder.vendorId || 'ACTIVE-VENDOR'}
              </p>
            </div>
          </div>

          {/* Line Items Table */}
          <div className="border border-slate-800 rounded-xl overflow-hidden print:border-gray-300">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] border-b border-slate-800">
                <tr>
                  <th className="px-4 py-3">Item Description</th>
                  <th className="px-4 py-3 text-center">Quantity</th>
                  <th className="px-4 py-3 text-right">Unit Price</th>
                  <th className="px-4 py-3 text-right">Total Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/80">
                <tr>
                  <td className="px-4 py-4 font-semibold text-slate-100">
                    <div>{selectedOrder.productName}</div>
                    <div className="text-[11px] text-slate-400 font-normal mt-0.5">
                      Procured via autonomous constraint-matching & pre-purchase revalidation
                    </div>
                  </td>
                  <td className="px-4 py-4 text-center font-bold text-slate-200">
                    {selectedOrder.quantity}
                  </td>
                  <td className="px-4 py-4 text-right font-mono text-slate-200">
                    {formatCurrency(selectedOrder.unitPrice)}
                  </td>
                  <td className="px-4 py-4 text-right font-mono font-bold text-emerald-400">
                    {formatCurrency(selectedOrder.totalAmount)}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Total Box */}
          <div className="flex justify-end pt-2">
            <div className="w-64 p-4 rounded-xl bg-slate-950/80 border border-slate-800 space-y-2 text-xs">
              <div className="flex justify-between text-slate-400">
                <span>Subtotal:</span>
                <span className="font-mono">{formatCurrency(selectedOrder.totalAmount)}</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>Taxes & Duties:</span>
                <span className="font-mono">Included</span>
              </div>
              <div className="flex justify-between text-sm font-bold text-slate-100 pt-2 border-t border-slate-800">
                <span>Total PO Amount:</span>
                <span className="text-emerald-400 font-mono">
                  {formatCurrency(selectedOrder.totalAmount)}
                </span>
              </div>
            </div>
          </div>

          {/* Legal / Governance Disclaimer */}
          <div className="pt-6 border-t border-slate-800 text-[11px] text-slate-500 leading-relaxed print:text-gray-600">
            <p>
              This Purchase Order is an authoritative corporate document executed deterministically through the Autonomous Procurement Engine state machine after passing inventory revalidation, price stability, and role-based limit authorization.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // 2. All Purchase Orders List View
  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-100 tracking-tight">Purchase Orders</h1>
          <p className="text-xs text-slate-400">All confirmed supplier orders generated by the authoritative core</p>
        </div>

        <button
          onClick={() => {
            setIsLoading(true);
            listPurchaseOrders().then((l) => {
              if (Array.isArray(l)) setOrders(l);
              setIsLoading(false);
            });
          }}
          disabled={isLoading}
          className="p-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition self-start sm:self-auto"
          title="Refresh"
        >
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      <div className="glass-panel rounded-xl border border-slate-800 overflow-hidden">
        {isLoading ? (
          <div className="p-12 text-center text-xs text-slate-400 space-y-2">
            <div className="w-6 h-6 border-2 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto"></div>
            <p>Loading confirmed purchase orders...</p>
          </div>
        ) : orders.length === 0 ? (
          <div className="p-12 text-center text-xs text-slate-400 space-y-2">
            <ShoppingBag className="w-8 h-8 text-slate-600 mx-auto" />
            <p>No purchase orders generated yet.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-slate-900/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                <tr>
                  <th className="px-5 py-3">PO Reference</th>
                  <th className="px-5 py-3">Vendor</th>
                  <th className="px-5 py-3">Product Ordered</th>
                  <th className="px-5 py-3">Quantity</th>
                  <th className="px-5 py-3 text-right">Total Amount</th>
                  <th className="px-5 py-3 text-center">Status</th>
                  <th className="px-5 py-3 text-right">Confirmed At</th>
                  <th className="px-5 py-3 text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {orders.map((po) => (
                  <tr key={po.id} className="hover:bg-slate-900/60 transition">
                    <td className="px-5 py-3.5 font-mono text-[11px] text-brand-300 font-bold">
                      {po.id ? `#${po.id.substring(0, 8)}` : 'PO-ORDER'}
                    </td>
                    <td className="px-5 py-3.5 font-semibold text-slate-100">{po.vendorName}</td>
                    <td className="px-5 py-3.5 font-medium text-slate-200">{po.productName}</td>
                    <td className="px-5 py-3.5">{po.quantity} units</td>
                    <td className="px-5 py-3.5 text-right font-mono font-bold text-emerald-400">
                      {formatCurrency(po.totalAmount)}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                        {po.status || 'CONFIRMED'}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-right text-slate-400">
                      {formatDate(po.confirmedAt || po.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <Link
                        to={`/purchase-orders/${po.procurementId || po.id}`}
                        className="px-3 py-1.5 rounded-md bg-slate-900 hover:bg-brand-600 hover:text-white text-slate-300 border border-slate-800 text-[11px] font-medium transition"
                      >
                        View PO
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
