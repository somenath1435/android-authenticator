package org.xwiki.android.sync.activities

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.xwiki.android.sync.R
import org.xwiki.android.sync.contactdb.UserAccount
import org.xwiki.android.sync.utils.AccountClickListener

class AccountListAdapter(
    private val mContext: Context,
    private var availableAccounts: List<UserAccount>,
    private val listener: AccountClickListener
) : RecyclerView.Adapter<AccountListAdapter.AccountListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountListViewHolder {

        val view = LayoutInflater.from(mContext).inflate(R.layout.account_list_layout, null)
        return AccountListViewHolder(view)
    }

    override fun getItemCount(): Int {
        return availableAccounts.size
    }

    override fun onBindViewHolder(holder: AccountListViewHolder, position: Int) {
        val account = availableAccounts.get(position)
        holder.tvAccountName.text = account.accountName
        holder.tvAccountServerAddress.text = account.serverAddress

        holder.llAccountItem.setOnClickListener {
            listener(account)
        }
    }

    class AccountListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAccountName: TextView
        val tvAccountServerAddress: TextView
        val llAccountItem: LinearLayout

        init {
            tvAccountName = view.findViewById(R.id.tvAccountName)
            tvAccountServerAddress = view.findViewById(R.id.tvAccountServerAddress)
            llAccountItem = view.findViewById(R.id.llAccountItem)
        }
    }
}

